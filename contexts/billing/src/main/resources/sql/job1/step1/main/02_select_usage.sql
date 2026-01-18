-- 특정 월에 대한 월별 사용량을 user_ids로 가지고 오는 SQL문
SELECT
    user_id,used_data_mb
FROM
    monthly_data_usage
WHERE
    user_id IN (:userIds)
    AND
    usage_year_month = :yearMonth