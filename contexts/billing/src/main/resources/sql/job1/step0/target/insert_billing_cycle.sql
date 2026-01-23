INSERT INTO billing_cycle (billing_month, status, cutoff_at)
VALUES (:billingMonth, 'RUNNING', :cutoffAt)
ON CONFLICT (billing_month)
    DO UPDATE SET cutoff_at = billing_cycle.cutoff_at,
                  status    = CASE
                                  WHEN billing_cycle.status = 'FINISHED' THEN billing_cycle.status
                                  ELSE 'RUNNING'
                      END
RETURNING billing_month, status, cutoff_at