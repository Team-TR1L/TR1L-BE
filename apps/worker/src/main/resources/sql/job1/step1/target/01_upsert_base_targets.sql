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
-- 이미 적재한 행은 덮어쓰지 않는다. 재실행 사이에 원천이 바뀌어도 처음 적재한 값으로 청구한다 (TR1L-179)
-- 덮어쓰면 이미 계산한 사용자의 청구서와 billing_targets 가 어긋나고, 같은 달 안에서 청구 기준이 섞인다
ON CONFLICT (billing_month, user_id) DO NOTHING
;