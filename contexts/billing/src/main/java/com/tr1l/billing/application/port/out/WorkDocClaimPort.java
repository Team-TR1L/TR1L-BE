package com.tr1l.billing.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

// 유스케이스에서 port로 받고 -> 실제 구현은 adapter
public interface WorkDocClaimPort {
    /**
     * TARGET(또는 lease 만료 PROCESSING) 작업을 선점해서 반환한다.
     * - status: TARGET -> PROCESSING
     * - leaseUntil: now + leaseDuration
     * - attemptCount: +1
     */
    List<ClaimedWorkDoc> claim(YearMonth billingMonth, int limit, Duration leaseDuration, String workerId, Instant now);

    record ClaimedWorkDoc(
            String id,          // billingMonth:userId
            String billingMonth, // "YYYY-MM-01"
            long userId,
            int attemptCount,
            Instant leaseUntil // 추가됨
    ) {}
}
