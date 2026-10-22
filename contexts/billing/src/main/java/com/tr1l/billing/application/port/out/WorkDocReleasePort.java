package com.tr1l.billing.application.port.out;

import java.time.Instant;
import java.time.LocalDate;

/**
 * billing_work 선점 반환
 * Step3 partition 이 실패했을 때 그 partition 이 claim 한 PROCESSING 을 TARGET 으로 돌려준다
 * - 첫 chunk 에서 commit 된 claim 이 남으면 lease 가 끝날 때까지 다시 가져갈 수 없다
 * - claimed_by 가 같은 work 만 돌려준다 (partition 마다 workerId 가 다르다)
 */
public interface WorkDocReleasePort {
    int releaseClaimed(LocalDate billingMonthDay, String workerId, Instant now);
}
