-- 청구 대상 사용자 한 명마다 입력 차원 값을 분류한다 (main DB)
-- 값 이름은 datasets/_dimensions/job1-input-dimensions.yml 과 같다
-- 분류는 Step1 기간 조건과 같은 기준을 쓴다
--   :monthStart 기준 월 첫날, :monthEnd 기준 월 마지막 날, :yearMonth 'YYYY-MM'
WITH target_users AS (
    SELECT u.user_id, u.plan_code, u.welfare_code
    FROM users u
    WHERE u.user_role = 'USER'
      AND u.user_status = 'ACTIVE'
      AND u.plan_code IS NOT NULL
),
contracts AS (
    SELECT DISTINCT ON (uc.user_id) uc.user_id, uc.start_date, uc.end_date
    FROM user_contract uc
    JOIN target_users t ON t.user_id = uc.user_id
    ORDER BY uc.user_id, uc.start_date DESC
),
soldiers AS (
    SELECT DISTINCT ON (us.user_id) us.user_id, us.start_date, us.end_date
    FROM user_soldier us
    JOIN target_users t ON t.user_id = us.user_id
    ORDER BY us.user_id, us.start_date DESC
),
options AS (
    SELECT s.user_id,
           count(*) AS option_count,
           bool_or(s.end_date >= CAST(:monthStart AS date)
                   AND s.end_date < CAST(:monthEnd AS date) + INTERVAL '1 day') AS cancelled_in_month
    FROM user_option_subscription s
    JOIN target_users t ON t.user_id = s.user_id
    GROUP BY s.user_id
)
SELECT t.user_id,
       CASE
           WHEN p.data_billing_type_code IN ('DBT-01', 'DBT-03') THEN 'unlimited'
           WHEN coalesce(m.used_data_mb, 0) < p.included_data_mb THEN 'below'
           WHEN coalesce(m.used_data_mb, 0) = p.included_data_mb THEN 'exactly'
           ELSE 'over'
       END AS usage,
       CASE
           WHEN c.user_id IS NULL THEN 'none'
           WHEN c.end_date < CAST(:monthStart AS date) THEN 'expired'
           WHEN c.end_date <= CAST(:monthEnd AS date) THEN 'ends_in_month'
           ELSE 'active'
       END AS contract,
       CASE
           WHEN sd.user_id IS NULL THEN 'none'
           WHEN sd.end_date IS NULL OR sd.end_date > CAST(:monthEnd AS date) THEN 'active'
           WHEN sd.end_date < CAST(:monthStart AS date) THEN 'expired'
           ELSE 'ends_in_month'
       END AS soldier,
       CASE WHEN t.welfare_code IS NULL OR t.welfare_code = 'WLF-01' THEN 'none' ELSE 'eligible' END AS welfare,
       CASE
           WHEN o.user_id IS NULL THEN '0'
           WHEN o.cancelled_in_month THEN 'mid_month'
           WHEN o.option_count = 1 THEN '1'
           WHEN o.option_count <= 3 THEN '2-3'
           ELSE '4-7'
       END AS options
FROM target_users t
JOIN plan p ON p.plan_code = t.plan_code
LEFT JOIN monthly_data_usage m ON m.user_id = t.user_id AND m.usage_year_month = :yearMonth
LEFT JOIN contracts c ON c.user_id = t.user_id
LEFT JOIN soldiers sd ON sd.user_id = t.user_id
LEFT JOIN options o ON o.user_id = t.user_id
