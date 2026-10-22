package com.tr1l.billing.adapter.out.persistence.jdbc;

public final class BillingWorkSQL {
    private BillingWorkSQL() {}

    public static final String MARK_CALCULATED = """
        UPDATE billing_work
        SET status      = 'CALCULATED',
            billing_id  = :billingId,
            lease_until = NULL,
            updated_at  = :now
        WHERE billing_month_day = :billingMonthDay
          AND user_id = :userId
          AND status = 'PROCESSING'
        """;

    public static final String MARK_FAILED = """
        UPDATE billing_work
        SET status        = 'FAILED',
            error_code    = :errorCode,
            error_message = :errorMessage,
            lease_until   = NULL,
            updated_at    = :now
        WHERE billing_month_day = :billingMonthDay
          AND user_id = :userId
          AND status = 'PROCESSING'
        """;

    // 실패한 partition 이 claim 한 work 를 다시 가져갈 수 있게 돌려준다
    public static final String RELEASE_CLAIMED = """
        UPDATE billing_work
        SET status      = 'TARGET',
            claimed_by  = NULL,
            lease_until = NULL,
            updated_at  = :now
        WHERE billing_month_day = :billingMonthDay
          AND claimed_by = :workerId
          AND status = 'PROCESSING'
        """;

}
