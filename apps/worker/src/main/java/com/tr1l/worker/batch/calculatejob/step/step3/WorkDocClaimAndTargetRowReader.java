package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.model.WorkAndTargetRow;
import com.tr1l.billing.application.port.out.BillingTargetLoadPort;
import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;

// Reader단계에서 ClaimPort를 통해 target인 유저 선점 후,
// BillingTargetLoadPort를 통해 벌크 연산을 통해 WorkAndTargetRow 조회
@Slf4j
public class WorkDocClaimAndTargetRowReader
        extends AbstractItemStreamItemReader<WorkAndTargetRow> {

    private final WorkDocClaimPort claimPort;
    private final BillingTargetLoadPort targetLoadPort;

    private final YearMonth billingMonth; // job 파라미터로 주입 2025-12
    private final int fetchSize; //
    private final Duration leaseDuration;
    private final String workerId;
    private final int partitionIndex;
    private final int partitionCount;

    private final Deque<WorkAndTargetRow> buffer = new ArrayDeque<>();
    private long totalClaimNanos = 0L;
    private long totalLoadNanos = 0L;
    private long totalBatches = 0L;

    public WorkDocClaimAndTargetRowReader(
            WorkDocClaimPort claimPort,
            BillingTargetLoadPort targetLoadPort,
            YearMonth billingMonth,
            int fetchSize,
            Duration leaseDuration,
            String workerId,
            int partitionIndex,
            int partitionCount
    ) {
        this.claimPort = claimPort;
        this.targetLoadPort = targetLoadPort;
        this.billingMonth = billingMonth;
        this.fetchSize = fetchSize;
        this.leaseDuration = leaseDuration;
        this.workerId = workerId;
        this.partitionIndex = partitionIndex;
        this.partitionCount = partitionCount;
        setName("step3WorkDocClaimAndTargetRowReader");
    }

    @Override
    public WorkAndTargetRow read() {
        if (buffer.isEmpty()) {
            Instant now = Instant.now();

            // billing_work에서 target인 유저를 PROCESSING claim 선점
            // status: target -> processing
            long claimStart = System.nanoTime();
            List<WorkDocClaimPort.ClaimedWorkDoc> claimed =
                    claimPort.claim(
                            billingMonth,
                            fetchSize,
                            leaseDuration,
                            workerId,
                            now,
                            partitionIndex,
                            partitionCount
                    );
            long claimNanos = System.nanoTime() - claimStart;

            if (claimed.isEmpty()) {
                return null; // 더 이상 처리할 work 없음
            }

            // 위 과정의 userId를 모아서 한번에 billing_targets에서 조회
            // 기존 -> 매 item마다 N번 조회되는 현상을 bulk 연산으로 수정
            List<Long> userIds = claimed.stream().map(WorkDocClaimPort.ClaimedWorkDoc::userId).distinct().toList();
            long loadStart = System.nanoTime();
            Map<Long, BillingTargetRow> rows = targetLoadPort.loadByUserIds(billingMonth, userIds);
            long loadNanos = System.nanoTime() - loadStart;

            //  work + row로 합쳐서 buffer에 넣고, read()는 1건씩 뽑아준다
            for (WorkDocClaimPort.ClaimedWorkDoc w : claimed) {
                buffer.addLast(new WorkAndTargetRow(w, rows.get(w.userId())));
            }

            totalClaimNanos += claimNanos;
            totalLoadNanos += loadNanos;
            totalBatches++;
            log.info(
                    "step3.reader batch={} claimed={} distinctUsers={} claimMs={} loadMs={} totalClaimMs={} totalLoadMs={}",
                    totalBatches,
                    claimed.size(),
                    userIds.size(),
                    claimNanos / 1_000_000,
                    loadNanos / 1_000_000,
                    totalClaimNanos / 1_000_000,
                    totalLoadNanos / 1_000_000
            );
        }

        return buffer.pollFirst();
    }
}
