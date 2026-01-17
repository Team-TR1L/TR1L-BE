package com.tr1l.billing.domain.model.entity;

import com.tr1l.billing.domain.model.enums.DiscountBasis;
import com.tr1l.billing.domain.model.enums.DiscountMethod;
import com.tr1l.billing.domain.model.enums.DiscountType;
import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.domain.model.vo.*;
import com.tr1l.billing.error.BillingErrorCode;

import java.util.Optional;

public final class DiscountLine {

    private final LineId lineId;
    private final DiscountType type;
    private final DiscountMethod method; // PERCENT or AMOUNT

    private final String displayName;
    private final SourceRef sourceRef;

    private final DiscountBasis basis;

    private final Money baseAmount;               // PERCENT면 필수, AMOUNT면 optional
    private final Rate rate;                      // PERCENT면 필수, AMOUNT면 null
    private final DiscountCap cap;                // optional (복지만 상한선 존재)

    private final Money discountAmount;           // 필수, >=0 (항상 양수/0)

    public DiscountLine(
            LineId lineId, // 할인 id
            DiscountType type, // 할인 유형
            DiscountMethod method, // % or amount
            String displayName, // 청구서 표시용
            SourceRef sourceRef, // 추적용
            DiscountBasis basis, // 할인 정책
            Money baseAmount, // 할인 정책이 적용되기 전의 금액
            Rate rate, // 할인 %, null 가능
            DiscountCap cap, // 복지일 경우에만 상한선 존재
            Money discountAmount // 할인 금액
    ) {
        if (lineId == null) throw new BillingDomainException(BillingErrorCode.INVALID_LINE_ID);
        if (type == null) throw new BillingDomainException(BillingErrorCode.INVALID_DISCOUNT_TYPE);
        if (method == null) throw new BillingDomainException(BillingErrorCode.INVALID_DISCOUNT_METHOD);
        if (displayName == null || displayName.isBlank()) throw new BillingDomainException(BillingErrorCode.INVALID_DISPLAY_NAME);
        if (basis == null) throw new BillingDomainException(BillingErrorCode.INVALID_DISCOUNT_BASIS);
        if (discountAmount == null) throw new BillingDomainException(BillingErrorCode.INVALID_DISCOUNT_AMOUNT);

        if (discountAmount.amount() < 0) {
            throw new BillingDomainException(BillingErrorCode.INVALID_DISCOUNT_AMOUNT);
        }

        // percent, amount 검증
        if (method == DiscountMethod.PERCENT) {
            if (rate == null) throw new BillingDomainException(BillingErrorCode.INVALID_RATE);
            if (baseAmount == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        } else if (method == DiscountMethod.AMOUNT) {
            if (rate != null) throw new BillingDomainException(BillingErrorCode.RATE_NOT_ALLOWED_FOR_AMOUNT_DISCOUNT);
            if (basis != DiscountBasis.SUBTOTAL_BEFORE_BUNDLE) throw new BillingDomainException(BillingErrorCode.INVALID_DISCOUNT_BASIS);
        }

        // 상한선은 항상 할인금액보다 크거나 같아야됨
        if (cap != null) {
            Money max = cap.maxDiscountAmount();
            if (discountAmount.amount() > max.amount()) {
                throw new BillingDomainException(BillingErrorCode.DISCOUNT_CAP_EXCEEDED);
            }
        }

        this.lineId = lineId;
        this.type = type;
        this.method = method;
        this.displayName = displayName.trim();
        this.sourceRef = sourceRef;
        this.basis = basis;

        this.baseAmount = baseAmount;
        this.rate = rate;
        this.cap = cap;

        this.discountAmount = discountAmount;
    }

    public LineId lineId() { return lineId; }
    public DiscountType type() { return type; }
    public DiscountMethod method() { return method; }
    public String displayName() { return displayName; }
    public SourceRef sourceRef() { return sourceRef; }

    public DiscountBasis basis() { return basis; }
    public Optional<Money> baseAmount() { return Optional.ofNullable(baseAmount); }
    public Optional<Rate> rate() { return Optional.ofNullable(rate); }
    public Optional<DiscountCap> cap() { return Optional.ofNullable(cap); }

    public Money discountAmount() { return discountAmount; }

    /** cap 적용 할인액 (방어적) */
    public Money effectiveDiscountAmount() {
        if (cap == null) return discountAmount;
        return discountAmount.min(cap.maxDiscountAmount());
    }

}
