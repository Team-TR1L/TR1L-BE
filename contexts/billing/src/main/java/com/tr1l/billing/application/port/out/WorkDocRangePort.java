package com.tr1l.billing.application.port.out;

import java.time.YearMonth;

/**
 * billing_work range for partitioning.
 */
public interface WorkDocRangePort {
    UserIdRange findUserIdRange(YearMonth billingMonth);

    record UserIdRange(long minUserId, long maxUserId) {}
}
