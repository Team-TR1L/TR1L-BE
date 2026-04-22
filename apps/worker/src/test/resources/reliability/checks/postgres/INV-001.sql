-- 같은 월 같은 사용자 work 중복 확인
-- rerun 뒤에도 이 값은 0 유지 기대
SELECT billing_month_day, user_id, COUNT(*) AS duplicate_count
FROM billing_work
WHERE billing_month_day = :billingMonthDay
GROUP BY billing_month_day, user_id
HAVING COUNT(*) > 1;
