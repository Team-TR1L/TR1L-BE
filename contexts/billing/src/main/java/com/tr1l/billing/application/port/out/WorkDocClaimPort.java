package com.tr1l.billing.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * MongDB billing_work에서 선점
 * TARGET(또는 lease 만료 PROCESSING) 작업을 선점
 * return
 * - status: TARGET -> PROCESSING
 * - leaseUntil: now + leaseDuration(넉넉하게 잡기)
 * - attemptCount: +1
 */
public interface WorkDocClaimPort {
    List<ClaimedWorkDoc> claim(
            YearMonth billingMonth,
            int limit,
            Duration leaseDuration,
            String workerId,
            Instant now,
            int partitionIndex,
            int partitionCount
    );

    record ClaimedWorkDoc(
            String id,          // billingMonth:userId -> "2026-01-01:12345"
            String billingMonth, // "YYYY-MM-01" -> "2026-01-01
            long userId, // 12345
            int attemptCount, // 0 -> 1++
            Instant leaseUntil // 추가됨

    ) {}
}
