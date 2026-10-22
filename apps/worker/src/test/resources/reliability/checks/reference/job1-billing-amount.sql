-- Job1 청구 금액 참조 계산 (C6)
-- 운영 계산 코드를 쓰지 않고 billing_targets 한 행만으로 다시 계산한다
-- 비율 할인은 모두 내림, 할인 순서는 약정 → 군인 → 복지
--   plan      = plan_monthly_price
--   overage   = DBT-02일 때 floor(excess_charge_per_mb * max(used - included, 0)), 나머지 요금 유형은 0
--   options   = options_jsonb 의 monthlyPrice 합
--   contract  = has_contract 일 때 floor(plan * contract_rate)
--   soldier   = soldier_eligible 일 때 floor(max(plan - contract, 0) * 0.20)
--   welfare   = welfare_eligible 일 때 floor((max(plan - contract - soldier, 0) + overage) * welfare_rate)
--               welfare_cap_amount 가 0보다 크면 그 값을 넘지 않음. 옵션 요금은 복지 할인 기준에 들어가지 않음
--   subtotal  = plan + overage + options
--   discount  = contract + soldier + welfare
--   total     = max(subtotal - discount, 0)
WITH base AS (
    SELECT user_id,
           plan_monthly_price AS plan,
           CASE
               WHEN data_billing_type_code = 'DBT-02'
                   THEN floor(excess_charge_per_mb * greatest(used_data_mb - included_data_mb, 0))::bigint
               ELSE 0
           END AS overage,
           coalesce((SELECT sum((option ->> 'monthlyPrice')::bigint)
                     FROM jsonb_array_elements(coalesce(options_jsonb, '[]'::jsonb)) AS option), 0) AS options,
           has_contract,
           contract_rate,
           soldier_eligible,
           welfare_eligible,
           welfare_rate,
           welfare_cap_amount
    FROM billing_targets
    WHERE billing_month = :billingMonthDay
),
contract AS (
    SELECT *,
           CASE WHEN has_contract THEN floor(plan * contract_rate)::bigint ELSE 0 END AS contract_discount
    FROM base
),
soldier AS (
    SELECT *,
           CASE
               WHEN soldier_eligible THEN floor(greatest(plan - contract_discount, 0) * 0.20)::bigint
               ELSE 0
           END AS soldier_discount
    FROM contract
),
welfare AS (
    SELECT *,
           CASE
               WHEN NOT welfare_eligible THEN 0
               WHEN welfare_cap_amount IS NOT NULL AND welfare_cap_amount > 0
                   THEN least(floor((greatest(plan - contract_discount - soldier_discount, 0) + overage) * welfare_rate)::bigint,
                              welfare_cap_amount)
               ELSE floor((greatest(plan - contract_discount - soldier_discount, 0) + overage) * welfare_rate)::bigint
           END AS welfare_discount
    FROM soldier
)
SELECT user_id,
       plan,
       overage,
       options,
       contract_discount,
       soldier_discount,
       welfare_discount,
       plan + overage + options AS subtotal,
       contract_discount + soldier_discount + welfare_discount AS discount_total,
       greatest(plan + overage + options - contract_discount - soldier_discount - welfare_discount, 0) AS total
FROM welfare
ORDER BY user_id
