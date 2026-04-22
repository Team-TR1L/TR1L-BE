-- 재실행 끝난 뒤 남아있는 stale processing 확인
-- lease 만료분이 남으면 회수 실패 의미
SELECT billing_month_day, user_id, status, lease_until
FROM billing_work
WHERE billing_month_day = :billingMonthDay
  AND status = 'PROCESSING'
  AND lease_until < now();
