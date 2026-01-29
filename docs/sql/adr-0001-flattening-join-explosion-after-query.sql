-- 1. 대상 유저 필터링 + 요금제/네트워크/무제한 타입/복지 코드

SELECT u.user_id AS user_id,
       u.name AS user_name,
       u.birth_date AS user_birth_date,
       u.phone_number AS recipient_phone,
       u.email AS recipient_email,

       p.plan_code AS plan_code,
       p.name AS plan_name,
       p.monthly_price AS plan_monthly_price,
       nt.network_type_name AS network_type_name,
       dbt.data_billing_type_code AS data_billing_type_code,
       dbt.data_billing_type_name AS data_billing_type_name,
       p.included_data_mb AS included_data_mb,
       p.excess_charge_per_mb AS excess_charge_per_mb,

       u.welfare_code AS welfare_code,
       COALESCE(w.welfare_name, '일반') AS welfare_name,
       COALESCE(w.discount_rate, 0) AS welfare_rate,
       COALESCE(w.max_discount, 0) AS welfare_cap_amount,

       u.from_time AS from_time,
       u.to_time AS to_time,
       u.day_time AS day_time

FROM users u
JOIN plan p ON p.plan_code = u.plan_code
JOIN network_type nt ON nt.network_type_code = p.network_type_code
JOIN data_billing_type dbt ON dbt.data_billing_type_code = p.data_billing_type_code
LEFT JOIN welfare_discount w ON w.welfare_code = u.welfare_code

WHERE u.user_role = 'USER'
  AND u.user_status = 'ACTIVE'
  AND u.plan_code IS NOT NULL
  AND u.user_id > ?

ORDER BY u.user_id
LiMIT ?;

--2. 특정 월에 대한 월별 사용량을 user_ids로 가지고 오는 SQL문
SELECT
    m.user_id,used_data_mb
FROM
    monthly_data_usage AS m
JOIN
    temp_user_ids AS t ON t.user_id = m.user_id
WHERE
    m.usage_year_month = :yearMonth

--3. 연월(year-month) 기준으로 유효한 약정 선택
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

--4. 유효한 군인 레코드 추출 (당월에 1일이라도 군인이면 할인)
-- [ parameter ]
-- usrIds : 유효한 유저의 아이디들
-- billingMonth : 정산 기준 월

SELECT uss.user_id
FROM temp_user_ids AS t
JOIN user_soldier AS uss
    ON t.user_id = uss.user_id
JOIN users AS u
    ON u.user_id = uss.user_id
JOIN plan AS p
    ON p.plan_code = u.plan_code
WHERE p.is_military_eligible = true -- 유저의 요금제가 군인 할인이 되는 경우
  AND uss.start_date < (:startDate::date + INTERVAL '1 month')::date
  AND (uss.end_date IS NULL OR uss.end_date >= :startDate::date);

--5. 유저가 가입한 부가 서비스 코드/이름/가격 조회
-- [ parameter ]
-- userIds : 유효한 유저 아이디
-- endDate : 정산 기준일 마지막 날

SELECT
  uos.user_id,
  os.option_service_code,
  os.option_service_name,
  COALESCE(os.monthly_price, 0) AS monthly_price

FROM temp_user_ids AS t
JOIN user_option_subscription AS uos
  ON t.user_id = uos.user_id
JOIN option_service os
  ON os.option_service_code = uos.option_service_code

WHERE uos.start_date < (:endDate::date + INTERVAL '1 day')
  AND uos.end_date   > :startDate::date;

--6. billing_targets INSERT 쿼리
INSERT INTO billing_targets (
    billing_month, user_id,

    from_time,to_time,day_time,

    user_name, user_birth_date, recipient_email, recipient_phone,

    plan_name, plan_monthly_price, network_type_name,
    data_billing_type_code, data_billing_type_name,
    included_data_mb, excess_charge_per_mb, used_data_mb,

    has_contract, contract_rate, contract_duration_months,

    soldier_eligible,

    welfare_eligible, welfare_code, welfare_name, welfare_rate, welfare_cap_amount,

    options_jsonb,send_option_jsonb
) VALUES (
    :billingMonth, :userId,

    CAST(:fromTime AS VARCHAR), CAST(:toTime AS VARCHAR), CAST(:dayTime AS VARCHAR),

    :userName, :userBirthDate, :recipientEmail, :recipientPhone,

    :planName, :planMonthlyPrice, :networkTypeName,
    :dataBillingTypeCode, :dataBillingTypeName,
    :includedDataMb, :excessChargePerMb, :usedDataMb,

    :hasContract, :contractRate, :contractDurationMonths,

    :soldierEligible,

    :welfareEligible, :welfareCode, :welfareName, :welfareRate, :welfareCapAmount,


    CAST(:optionsJson AS jsonb),
CAST(:sendOptionJson AS jsonb)
)
ON CONFLICT (billing_month, user_id)
DO UPDATE SET
    user_name = EXCLUDED.user_name,
    user_birth_date = EXCLUDED.user_birth_date,
    recipient_email = EXCLUDED.recipient_email,
    recipient_phone = EXCLUDED.recipient_phone,

    plan_name = EXCLUDED.plan_name,
    plan_monthly_price = EXCLUDED.plan_monthly_price,
    network_type_name = EXCLUDED.network_type_name,
    data_billing_type_code = EXCLUDED.data_billing_type_code,
    data_billing_type_name = EXCLUDED.data_billing_type_name,
    included_data_mb = EXCLUDED.included_data_mb,
    excess_charge_per_mb = EXCLUDED.excess_charge_per_mb,
    used_data_mb = EXCLUDED.used_data_mb,

    has_contract = EXCLUDED.has_contract,
    contract_rate = EXCLUDED.contract_rate,
    contract_duration_months = EXCLUDED.contract_duration_months,

    soldier_eligible = EXCLUDED.soldier_eligible,

    welfare_eligible = EXCLUDED.welfare_eligible,
    welfare_code = EXCLUDED.welfare_code,
    welfare_name = EXCLUDED.welfare_name,
    welfare_rate = EXCLUDED.welfare_rate,
    welfare_cap_amount = EXCLUDED.welfare_cap_amount,

    from_time = EXCLUDED.from_time,
    to_time = EXCLUDED.to_time,
    day_time = EXCLUDED.day_time,

    options_jsonb = EXCLUDED.options_jsonb,
    send_option_jsonb=EXCLUDED.send_option_jsonb
;
