INSERT INTO billing_targets (
    billing_month, user_id,

    user_name, user_birth_date, recipient_email, recipient_phone,

    plan_name, plan_monthly_price, network_type_name,
    data_billing_type_code, data_billing_type_name,
    included_data_mb, excess_charge_per_mb, used_data_mb,

    has_contract, contract_rate, contract_duration_months,

    soldier_eligible,

    welfare_eligible, welfare_code, welfare_name, welfare_rate, welfare_cap_amount,

    options_jsonb
) VALUES (
    :billingMonth, :userId,

    :userName, :userBirthDate, :recipientEmail, :recipientPhone,

    :planName, :planMonthlyPrice, :networkTypeName,
    :dataBillingTypeCode, :dataBillingTypeName,
    :includedDataMb, :excessChargePerMb, :usedDataMb,

    :hasContract, :contractRate, :contractDurationMonths,

    :soldierEligible,

    :welfareEligible, :welfareCode, :welfareName, :welfareRate, :welfareCapAmount,


    CAST(:optionsJson AS jsonb)
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

    options_jsonb = EXCLUDED.options_jsonb
;
