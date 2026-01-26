UPDATE billing_work
        SET status        = 'FAILED',
            error_code    = :errorCode,
            error_message = :errorMessage,
            lease_until   = NULL,
            updated_at    = :now
        WHERE billing_month_day = :billingMonthDay
          AND user_id = :userId
          AND status = 'PROCESSING'