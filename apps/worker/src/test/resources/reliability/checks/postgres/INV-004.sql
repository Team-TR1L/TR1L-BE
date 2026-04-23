-- target 과 work 사용자 집합 차이 확인
-- 누락 또는 고아 work 모두 재실행 안전성 위협
SELECT COALESCE(bt.user_id, bw.user_id) AS user_id,
       bt.billing_month AS target_billing_month,
       bw.billing_month_day AS work_billing_month_day,
       CASE
           WHEN bt.user_id IS NULL THEN 'WORK_WITHOUT_TARGET'
           WHEN bw.user_id IS NULL THEN 'TARGET_WITHOUT_WORK'
       END AS mismatch_type
FROM billing_targets bt
FULL OUTER JOIN billing_work bw
    ON bw.billing_month_day = bt.billing_month
   AND bw.user_id = bt.user_id
WHERE COALESCE(bt.billing_month, bw.billing_month_day) = :billingMonthDay
  AND (bt.user_id IS NULL OR bw.user_id IS NULL);
