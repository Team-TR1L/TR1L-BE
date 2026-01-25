-- 특정 월에 대한 월별 사용량을 user_ids로 가지고 오는 SQL문
SELECT
    m.user_id,used_data_mb
FROM
    monthly_data_usage AS m
JOIN
    temp_user_ids AS t ON t.user_id = m.user_id
WHERE
    m.usage_year_month = :yearMonth