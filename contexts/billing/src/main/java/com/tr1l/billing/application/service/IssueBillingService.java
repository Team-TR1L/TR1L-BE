package com.tr1l.billing.application.service;

import com.tr1l.billing.adapter.out.persistence.dto.BillingTargetRow;
import com.tr1l.billing.adapter.out.persistence.mapper.BillingTargetRowAssembler;
import com.tr1l.billing.application.port.out.BillingIdGenerator;
import com.tr1l.billing.application.port.out.BillingSnapshotSavePort;
import com.tr1l.billing.domain.event.DomainEvent;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.billing.domain.model.vo.BillingId;
import com.tr1l.billing.domain.model.vo.CustomerId;
import com.tr1l.billing.domain.model.vo.IdempotencyKey;
import com.tr1l.billing.domain.model.vo.LineId;
import com.tr1l.billing.domain.service.BillingCalculator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class IssueBillingService {
    private final BillingTargetRowAssembler assembler;
    private final BillingSnapshotSavePort billingSnapshotSavePort;
    private final BillingIdGenerator billingIdGenerator;
    private final Clock clock;

    public IssueBillingService(
            BillingTargetRowAssembler assembler,
            BillingSnapshotSavePort billingSnapshotSavePort,
            BillingIdGenerator billingIdGenerator,
            Clock clock
    ) {
        this.assembler = Objects.requireNonNull(assembler);
        this.billingSnapshotSavePort = Objects.requireNonNull(billingSnapshotSavePort);
        this.billingIdGenerator = Objects.requireNonNull(billingIdGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Step2 Processor에서 row 1건 처리하는 "유스케이스"
     * - Row -> Billing(DRAFT) 조립(도메인 계산)
     * - issue()로 발행
     * - Billing 스냅샷을 MongoDB에 저장
     * - DomainEvent는 뽑아서 반환(지금은 저장 안한다면 그냥 무시해도 됨)
     */
    public BillingIssueResult issueAndSave(CustomerId customerId, BillingTargetRow row) {
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(row);

        // 1) 멱등키 생성 (MVP: 결정성 있게 만들어서 배치 재실행 시 동일)
        BillingId billingId = billingIdGenerator.nextId();
        IdempotencyKey idempotencyKey = createIdempotencyKey(customerId, row.billingMonth());

        // 2) 라인ID 공급자(청구/할인 라인 순번)
        BillingCalculator.LineIdProvider lineIdProvider = newSequentialLineIdProvider();

        // 3) Row -> Billing(DRAFT) (계산 + 라인구성 + totals)
        Billing draft = assembler.toDraftBilling(
                billingId,
                customerId,
                idempotencyKey,
                row,
                lineIdProvider
        );

        // 4) 발행
        Instant now = Instant.now(clock);
        draft.issue(now);

        // 5) 도메인 이벤트는 Billing 스냅샷에 넣지 않도록 pull 해서 비움
        List<DomainEvent> events = draft.pullDomainEvents();

        // 6) MongoDB 저장 (그대로)
        billingSnapshotSavePort.save(draft);

        return new BillingIssueResult(draft, events);
    }

    private IdempotencyKey createIdempotencyKey(CustomerId customerId, String billingMonth) {
        // 예: 2026-01:customerIdValue
        String v = billingMonth + ":" + customerId.value();
        return new IdempotencyKey(v);
    }

    private BillingCalculator.LineIdProvider newSequentialLineIdProvider() {
        AtomicLong seq = new AtomicLong(1);
        return () -> new LineId(seq.getAndIncrement());
    }

    public record BillingIssueResult(Billing billing, List<DomainEvent> events) {
        public BillingIssueResult {
            billing = Objects.requireNonNull(billing);
            events = (events == null) ? List.of() : List.copyOf(events);
        }
    }
}
