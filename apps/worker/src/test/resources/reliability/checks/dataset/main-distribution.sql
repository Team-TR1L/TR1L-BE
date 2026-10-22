-- 데이터셋 분포 실측 (main DB)
-- dataset.yml distribution 항목과 같은 이름으로 센다
-- :monthStart 기준 월 첫날, :monthEnd 기준 월 마지막 날, :yearMonth 'YYYY-MM'
WITH target_users AS (
    SELECT u.user_id, u.plan_code
    FROM users u
    WHERE u.user_status = 'ACTIVE'
      AND u.plan_code IS NOT NULL
),
usage_pattern AS (
    SELECT CASE
               WHEN p.data_billing_type_code IN ('DBT-01', 'DBT-03') THEN 'unlimitedPlanRatio'
               WHEN m.used_data_mb < p.included_data_mb THEN 'belowIncludedDataRatio'
               WHEN m.used_data_mb = p.included_data_mb THEN 'exactlyIncludedDataRatio'
               ELSE 'overIncludedDataRatio'
           END AS metric
    FROM target_users t
    JOIN plan p ON p.plan_code = t.plan_code
    JOIN monthly_data_usage m ON m.user_id = t.user_id AND m.usage_year_month = :yearMonth
),
option_counts AS (
    SELECT t.user_id, count(s.option_service_code) AS option_count
    FROM target_users t
    LEFT JOIN user_option_subscription s ON s.user_id = t.user_id
    GROUP BY t.user_id
)
SELECT 'users.activeWithPlan' AS metric, count(*) AS actual FROM target_users
UNION ALL
SELECT 'users.withdrawn', count(*) FROM users WHERE user_status = 'WITHDRAWN'
UNION ALL
SELECT 'users.banned', count(*) FROM users WHERE user_status = 'BANNED'
UNION ALL
SELECT 'users.activeWithoutPlan', count(*) FROM users WHERE user_status = 'ACTIVE' AND plan_code IS NULL
UNION ALL
SELECT 'usage.targetMonthRows', count(*) FROM monthly_data_usage WHERE usage_year_month = :yearMonth
UNION ALL
SELECT 'usage.previousMonthNoiseRows', count(*) FROM monthly_data_usage
WHERE usage_year_month = to_char(CAST(:monthStart AS date) - INTERVAL '1 month', 'YYYY-MM')
UNION ALL
SELECT 'usage.nextMonthNoiseRows', count(*) FROM monthly_data_usage
WHERE usage_year_month = to_char(CAST(:monthStart AS date) + INTERVAL '1 month', 'YYYY-MM')
UNION ALL
SELECT 'usagePattern.' || metric, count(*) FROM usage_pattern GROUP BY metric
UNION ALL
SELECT 'contract.activeCount', count(*) FROM user_contract
WHERE start_date <= :monthEnd AND end_date >= :monthStart
UNION ALL
SELECT 'contract.expiredCount', count(*) FROM user_contract WHERE end_date < :monthStart
UNION ALL
SELECT 'welfare.eligibleCount', count(*) FROM users WHERE welfare_code IS NOT NULL AND welfare_code <> 'WLF-01'
UNION ALL
SELECT 'soldier.activeCount', count(*) FROM user_soldier
WHERE start_date <= :monthEnd AND (end_date IS NULL OR end_date >= :monthStart)
UNION ALL
SELECT 'soldier.expiredCount', count(*) FROM user_soldier WHERE end_date < :monthStart
UNION ALL
SELECT 'options.expectedTotalSubscriptions', coalesce(sum(option_count), 0) FROM option_counts
UNION ALL
SELECT 'options.noOptionRatio', count(*) FROM option_counts WHERE option_count = 0
UNION ALL
SELECT 'options.oneOptionRatio', count(*) FROM option_counts WHERE option_count = 1
UNION ALL
SELECT 'options.twoOrThreeOptionsRatio', count(*) FROM option_counts WHERE option_count BETWEEN 2 AND 3
UNION ALL
SELECT 'options.fourToSevenOptionsRatio', count(*) FROM option_counts WHERE option_count BETWEEN 4 AND 7
