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

}
