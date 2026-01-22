package com.tr1l.billing.application.service;

import com.tr1l.billing.application.assembler.BillingTargetRowAssembler;
import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.port.out.BillingIdGenerator;
import com.tr1l.billing.domain.event.DomainEvent;
import com.tr1l.billing.domain.model.aggregate.Billing;
import com.tr1l.billing.domain.model.vo.BillingId;
import com.tr1l.billing.domain.model.vo.CustomerId;
import com.tr1l.billing.domain.model.vo.IdempotencyKey;
import com.tr1l.billing.domain.model.vo.LineId;
import com.tr1l.billing.domain.service.BillingCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class IssueBillingService {
    private final BillingTargetRowAssembler assembler;
    private final BillingIdGenerator billingIdGenerator;
    private final Clock clock;

    public IssueBillingService(
            BillingTargetRowAssembler assembler,
            BillingIdGenerator billingIdGenerator,
            Clock clock
    ) {
        this.assembler = Objects.requireNonNull(assembler);
        this.billingIdGenerator = Objects.requireNonNull(billingIdGenerator);
        this.clock = Objects.requireNonNull(clock);
    }


    /**
     * Billing을 생성하여, 내부 값을 채우고 Issued 처리
     */
    public BillingIssueResult issueOnly(BillingTargetRow row, String workId) {
        Objects.requireNonNull(row);
        Objects.requireNonNull(workId);

        IdempotencyKey idempotencyKey = new IdempotencyKey(workId);
        BillingId billingId = billingIdGenerator.generateForWork(workId);
        CustomerId customerId = new CustomerId(row.userId());
        BillingCalculator.LineIdProvider lineIdProvider = newSequentialLineIdProvider();

        // 계산이 완료된 청구서
        Billing draft = assembler.toDraftBilling(
                billingId, customerId, idempotencyKey, row, lineIdProvider
        );

        //
        Instant now = Instant.now(clock);
        draft.issue(now);

        List<DomainEvent> events = draft.pullDomainEvents();
        return new BillingIssueResult(draft, events);
    }



    // 각 청구, 할인 라인마다 새로운 id 부여
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
