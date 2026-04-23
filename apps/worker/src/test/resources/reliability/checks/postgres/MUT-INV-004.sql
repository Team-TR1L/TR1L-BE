-- INV-004 를 일부러 느슨하게 만든 mutant
-- 불일치가 5개 이하면 놓치는 조건
WITH mismatch AS (
    SELECT COALESCE(bt.user_id, bw.user_id) AS user_id
    FROM billing_targets bt
    FULL OUTER JOIN billing_work bw
        ON bw.billing_month_day = bt.billing_month
       AND bw.user_id = bt.user_id
    WHERE COALESCE(bt.billing_month, bw.billing_month_day) = :billingMonthDay
      AND (bt.user_id IS NULL OR bw.user_id IS NULL)
)
SELECT COUNT(*) AS mismatch_count
FROM mismatch
HAVING COUNT(*) > 5;
