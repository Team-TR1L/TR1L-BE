package com.tr1l.billing.application.service;

import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.assembler.BillingTargetRowAssembler;
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

// service(유스케이스) -> port 호출
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
    public BillingIssueResult issueAndSave(BillingTargetRow row,  String workId) {
        Objects.requireNonNull(row);
        Objects.requireNonNull(workId);

        // 1) 멱등키 = workId
        IdempotencyKey idempotencyKey = new IdempotencyKey(workId);

        // 2) BillingId = workId 기반 결정적 생성
        BillingId billingId = billingIdGenerator.generateForWork(workId);

        // 3) CustomerId는 row 기반(이미 userId 있음)
        CustomerId customerId = new CustomerId(row.userId());

        // 4) 라인ID 공급자
        BillingCalculator.LineIdProvider lineIdProvider = newSequentialLineIdProvider();

        // 5) Row -> Billing(DRAFT) (계산 + 라인구성 + totals)
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

        // 7) 이벤트 pull (필요 없으면 무시)
        List<DomainEvent> events = draft.pullDomainEvents();

        // 8) MongoDB 스냅샷 저장 //
        billingSnapshotSavePort.save(draft);

        return new BillingIssueResult(draft, events);
    }

//    private IdempotencyKey createIdempotencyKey(CustomerId customerId, String billingMonth) {
//        // 예: 2026-01:customerIdValue
//        String v = billingMonth + ":" + customerId.value();
//        return new IdempotencyKey(v);
//    }

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
