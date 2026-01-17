package com.tr1l.billing.domain.model.aggregate;

import com.tr1l.billing.api.event.BillingIssuedEvent;
import com.tr1l.billing.domain.model.enums.BillingStatus;
import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.domain.model.entity.ChargeLine;
import com.tr1l.billing.domain.model.entity.DiscountLine;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.error.BillingErrorCode;
import com.tr1l.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Billing {

    private final BillingId billingId; // 청구서 식별자
    private final CustomerId customerId; // 고객 식별자
    private final BillingPeriod period; // 청구년월, UNIQUE(고객, period )

    private BillingStatus status; // DRAFT, ISSUED, CANCELLED

    private Recipient recipient; // DRAFT에서만 변경 가능
    private final IdempotencyKey idempotencyKey; // 배치 재실행, 유니크

    private final List<ChargeLine> chargeLines = new ArrayList<>(); // issued 이후 수정 x
    private final List<DiscountLine> discountLines = new ArrayList<>(); // issued 이후 수정 금지, 할인액은 항상 양수

    private Money subtotalAmount = Money.zero(); // chargeLines.lineAMount 합계
    private Money discountTotalAmount = Money.zero(); // discountLines.discountAmount 합계
    private Money totalAmount = Money.zero(); // 최종 청구 금액

    private Instant issuedAt; // ISSUED에서만 존재

    /** 추가 생성(MVP용)*/
    private final CustomerName customerName;
    private final CustomerBirthDate customerBirthDate;

    // Outbox 연계용(도메인 이벤트 적재)
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Billing(
            BillingId billingId,
            CustomerId customerId,
            BillingPeriod period,
            CustomerName customerName,
            CustomerBirthDate customerBirthDate,
            Recipient recipient,
            IdempotencyKey idempotencyKey
    ) {
        if (billingId == null) throw new BillingDomainException(BillingErrorCode.INVALID_BILLING_ID);
        if (customerId == null) throw new BillingDomainException(BillingErrorCode.INVALID_CUSTOMER_ID);
        if (period == null) throw new BillingDomainException(BillingErrorCode.INVALID_BILLING_PERIOD);

        if (customerName == null) throw new BillingDomainException(BillingErrorCode.INVALID_USER_NAME);
        if (customerBirthDate == null) throw new BillingDomainException(BillingErrorCode.INVALID_USER_BIRTH_DATE);

        if (recipient == null) throw new BillingDomainException(BillingErrorCode.INVALID_RECIPIENT);
        if (idempotencyKey == null) throw new BillingDomainException(BillingErrorCode.INVALID_IDEMPOTENCY_KEY);

        this.billingId = billingId;
        this.customerId = customerId;
        this.period = period;

        this.customerName = customerName;
        this.customerBirthDate = customerBirthDate;

        this.recipient = recipient;
        this.idempotencyKey = idempotencyKey;

        // 규칙 적용
        this.status = BillingStatus.DRAFT;
        this.subtotalAmount = Money.zero();
        this.discountTotalAmount = Money.zero();
        this.totalAmount = Money.zero();
        this.issuedAt = null;
    }

    /** 청구서 초안 생성 (status=DRAFT, totals=0, issuedAt = null) */
    public static Billing draft(
            BillingId billingId,
            CustomerId customerId,
            BillingPeriod period,
            CustomerName customerName,
            CustomerBirthDate customerBirthDate,
            Recipient recipient,
            IdempotencyKey idempotencyKey
    ) {
        return new Billing(billingId,
                customerId,
                period,
                customerName,
                customerBirthDate,
                recipient,
                idempotencyKey);
    }

    // =========================
    // Behavior (DRAFT only)
    // =========================

    // DRAFT에서만 수신자 변경 가능
    public void changeRecipient(Recipient newRecipient) {
        requireDraft();
        if (newRecipient == null) throw new BillingDomainException(BillingErrorCode.INVALID_RECIPIENT);
        this.recipient = newRecipient;
    }

    // 청구 라인 추가
    public void addChargeLine(ChargeLine line) {
        requireDraft();
        if (line == null) throw new BillingDomainException(BillingErrorCode.INVALID_CHARGE_LINE);
        this.chargeLines.add(line);
    }

    // 할인 라인 추가
    public void addDiscountLine(DiscountLine line) {
        requireDraft();
        if (line == null) throw new BillingDomainException(BillingErrorCode.INVALID_DISCOUNT_LINE);
        this.discountLines.add(line);
    }

    /** 소계/할인합/최종합 재계산 (DRAFT에서만) */
    public void recalculateTotals() {
        requireDraft();

        // 청구 금액
        Money subtotal = Money.zero();
        for (ChargeLine cl : chargeLines) {
            subtotal = subtotal.plus(cl.lineAmount());
        }

        // 총 할인 금액
        Money discountTotal = Money.zero();
        for (DiscountLine dl : discountLines) {
            discountTotal = discountTotal.plus(dl.effectiveDiscountAmount());
        }

        Money total = subtotal.minusNonNegative(discountTotal);
        if (total.amount() < 0) {
            throw new BillingDomainException(BillingErrorCode.INVALID_BILLING_AMOUNT);
        }

        this.subtotalAmount = subtotal;
        this.discountTotalAmount = discountTotal;
        this.totalAmount = total;
    }

    /**
     * 규칙: 청구서 발행 완료
     * 1) status==DRAFT
     * 2) recalculate 실행
     * 3) status=ISSUED, issuedAt=now
     * 4) 도메인 이벤트 발생(적재)
     */
    public void issue(Instant issuedAt) {
        requireDraft(); // DRAFT 상태가 아니면 예외처리

        if (issuedAt == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_ISSUED_AT);
        }

        // Issued 될 시점에 발송 수단 1개는 필수 존재
        if (!recipient.hasAnyContact()) {
            throw new BillingDomainException(BillingErrorCode.MISSING_RECIPIENT_CONTACT);
        }

        // totals는 issue 직전에 항상 재계산 (결정성/정합성)
        recalculateTotals();

        // 상태 변경 및 시간 기록
        this.status = BillingStatus.ISSUED;
        this.issuedAt = issuedAt;

        // 도메인에서는 이벤트만 적재함
        domainEvents.add(new BillingIssuedEvent(
                billingId.value(),
                customerId.value(),
                period.value(),
                issuedAt
        ));
    }

    /**
     * 규칙: 청구서 취소(status=CANCELLED)
     * - 스냅샷 불변(ISSUED 이후 수정 금지)을 지키기 위해 DRAFT에서만 허용
     */
    public void cancel() {
        requireDraft();
        this.status = BillingStatus.CANCELLED;
    }

    /** Outbox 연계용 이벤트 추출 */
    public List<DomainEvent> pullDomainEvents() {
        if (domainEvents.isEmpty()) return List.of();
        List<DomainEvent> copied = List.copyOf(domainEvents);
        domainEvents.clear();
        return copied;
    }

    // =========================
    // Guards
    // =========================
    private void requireDraft() {
        if (status != BillingStatus.DRAFT) {
            throw new BillingDomainException(BillingErrorCode.BILLING_NOT_MUTABLE);
        }
    }

    // =========================
    // Getters (읽기 전용)
    // =========================
    public BillingId billingId() { return billingId; }
    public CustomerId customerId() { return customerId; }
    public BillingPeriod period() { return period; }
    public BillingStatus status() { return status; }
    public Recipient recipient() { return recipient; }
    public IdempotencyKey idempotencyKey() { return idempotencyKey; }

    public CustomerName customerName() { return customerName; }
    public CustomerBirthDate customerBirthDate() { return customerBirthDate; }

    public List<ChargeLine> chargeLines() { return Collections.unmodifiableList(chargeLines); }
    public List<DiscountLine> discountLines() { return Collections.unmodifiableList(discountLines); }

    public Money subtotalAmount() { return subtotalAmount; }
    public Money discountTotalAmount() { return discountTotalAmount; }
    public Money totalAmount() { return totalAmount; }

    public Instant issuedAt() { return issuedAt; }
}
