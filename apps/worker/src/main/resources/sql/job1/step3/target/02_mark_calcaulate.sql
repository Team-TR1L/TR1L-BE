UPDATE billing_work
        SET status      = 'CALCULATED',
            billing_id  = :billingId,
            lease_until = NULL,
            updated_at  = :now
        WHERE billing_month_day = :billingMonthDay
          AND user_id = :userId
          AND status = 'PROCESSING'