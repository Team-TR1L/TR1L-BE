package com.tr1l.worker.batch.calculatejob.step.step3;

import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.model.WorkAndTargetRow;
import com.tr1l.billing.application.port.out.BillingTargetLoadPort;
import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import com.tr1l.worker.batch.calculatejob.model.BillingTargetKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

// Reader단계에서 ClaimPort를 통해 target인 유저 선점 후,
// BillingTargetLoadPort를 통해 벌크 연산을 통해 WorkAndTargetRow 조회
@Slf4j
public class WorkDocClaimAndTargetRowReader
        extends AbstractItemStreamItemReader<WorkAndTargetRow> {

    private final WorkDocClaimPort claimPort;
    private final BillingTargetLoadPort targetLoadPort;

    private final YearMonth billingMonth;
    private final int fetchSize;
    private final Duration leaseDuration;
    private final String workerId;
    private final int partitionIndex;
    private final int partitionCount;

    // 멀티스레드 환경 보호
    private final Deque<WorkAndTargetRow> buffer = new ArrayDeque<>();

    // read() 멀티스레드 동시 호출 보호
    private final ReentrantLock lock = new ReentrantLock();

    // end-of-stream 플래그
    private volatile boolean exhausted = false;
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
        lock.lock();
        try {
            if (exhausted) return null;

            // buffer가 비어있으면 새 batch claim해서 채움
            while (buffer.isEmpty()) {
                Instant now = Instant.now();

                long claimStart = System.nanoTime();
                List<WorkDocClaimPort.ClaimedWorkDoc> claimed = claimPort.claim(
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
                    exhausted = true;
                    return null;
                }

                List<Long> userIds = claimed.stream()
                        .map(WorkDocClaimPort.ClaimedWorkDoc::userId)
                        .distinct()
                        .toList();

                long loadStart = System.nanoTime();
                Map<Long, BillingTargetRow> rows = targetLoadPort.loadByUserIds(billingMonth, userIds);
                long loadNanos = System.nanoTime() - loadStart;

                int missing = 0;
                for (WorkDocClaimPort.ClaimedWorkDoc w : claimed) {
                    BillingTargetRow row = rows.get(w.userId());
                    if (row == null) {
                        missing++;
                        // 운영적으로는 여기서 billing_work를 FAILED로 마킹하는게 베스트지만,
                        // 지금 포트에 없으니 일단 스킵 + 로그
                        continue;
                    }
                    buffer.addLast(new WorkAndTargetRow(w, row));
                }

                totalClaimNanos += claimNanos;
                totalLoadNanos += loadNanos;
                totalBatches++;

                log.info(
                        "step3.reader batch={} claimed={} distinctUsers={} missingRows={} claimMs={} loadMs={} totalClaimMs={} totalLoadMs={}",
                        totalBatches,
                        claimed.size(),
                        userIds.size(),
                        missing,
                        claimNanos / 1_000_000,
                        loadNanos / 1_000_000,
                        totalClaimNanos / 1_000_000,
                        totalLoadNanos / 1_000_000
                );

                // claimed는 있는데 전부 missing이면 다음 batch로 재시도
                if (buffer.isEmpty()) {
                    continue;
                }
            }

            return buffer.pollFirst();

        } finally {
            lock.unlock();
        }
    }
}
