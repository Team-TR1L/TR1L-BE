WITH cte AS (SELECT billing_month_day, user_id
             FROM billing_work
             WHERE billing_month_day = :billingMonthDay
               AND (
                 status = 'TARGET'
                     OR (status = 'PROCESSING' AND lease_until IS NOT NULL AND lease_until < :now)
                 )
               AND mod(user_id, :partitionCount) = :partitionIndex
             ORDER BY user_id
             LIMIT :limit FOR UPDATE SKIP LOCKED)
UPDATE billing_work bw
SET status        = 'PROCESSING',
    lease_until   = :leaseUntil,
    attempt_count = bw.attempt_count + 1,
    claimed_by    = :workerId,
    updated_at    = :now
FROM cte
WHERE bw.billing_month_day = cte.billing_month_day
  AND bw.user_id = cte.user_id
RETURNING bw.billing_month_day, bw.user_id, bw.attempt_count