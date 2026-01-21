package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

// Claim 기반 Reader
public class WorkDocClaimReader extends AbstractItemStreamItemReader<WorkDocClaimPort.ClaimedWorkDoc> {

    private final WorkDocClaimPort claimPort;
    private final YearMonth billingMonth;
    private final int fetchSize;
    private final Duration leaseDuration;
    private final String workerId;

    private final Deque<WorkDocClaimPort.ClaimedWorkDoc> buffer = new ArrayDeque<>();

    public WorkDocClaimReader(
            WorkDocClaimPort claimPort,
            YearMonth billingMonth,
            int fetchSize,
            Duration leaseDuration,
            String workerId
    ) {
        this.claimPort = claimPort;
        this.billingMonth = billingMonth;
        this.fetchSize = fetchSize;
        this.leaseDuration = leaseDuration;
        this.workerId = workerId;
        setName("step3WorkDocClaimReader");
    }

    @Override
    public WorkDocClaimPort.ClaimedWorkDoc read() {
        if (buffer.isEmpty()) { // 버퍼가 비어 있으면 item을 가져옴
            Instant now = Instant.now();
            List<WorkDocClaimPort.ClaimedWorkDoc> claimed =
                    claimPort.claim(billingMonth, fetchSize, leaseDuration, workerId, now);

            if (claimed.isEmpty()) return null; // 더 이상 처리할 work가 없음 -> step 종료
            buffer.addAll(claimed);
        }
        return buffer.pollFirst();
    }
}