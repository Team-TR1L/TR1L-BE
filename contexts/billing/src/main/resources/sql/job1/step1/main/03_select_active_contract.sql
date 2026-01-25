-- 연월(year-month) 기준으로 유효한 약정 선택
-- [ parameter ]
-- userIds   : 유효한 유저들 아이디
-- startDate : YearMonth 기준으로 해당 월의 시작일 1일
-- endDate   : YearMonth 기준으로 해당 월의 마지막 일
WITH active_contract AS (
    SELECT DISTINCT ON (uc.user_id)
        uc.user_id,
        (date_part('year', age(uc.end_date, uc.start_date))::int * 12
            + date_part('month', age(uc.end_date, uc.start_date))::int) AS duration_months

    FROM temp_user_ids AS t

    JOIN user_contract AS uc ON t.user_id = uc.user_id

    WHERE uc.start_date <= :endDate::date
      AND uc.end_date >= :startDate::date
    ORDER BY uc.user_id , uc.start_date DESC
)

SELECT ac.user_id, ac.duration_months, COALESCE(cd.discount_percent,0) AS discount_percent
FROM active_contract AS ac
LEFT JOIN contract_discount AS cd
    ON ac.duration_months = cd.duration_months;