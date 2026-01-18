-- 대상 유저 필터링 + 요금제/네트워크/무제한 타입/복지 코드

SELECT
  u.user_id user_id,
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
  COALESCE(w.max_discount, 0) AS welfare_cap_amount

FROM users u
JOIN plan p ON p.plan_code = u.plan_code
JOIN network_type nt ON nt.network_type_code = p.network_type_code
JOIN data_billing_type dbt ON dbt.data_billing_type_code = p.data_billing_type_code
LEFT JOIN welfare_discount w ON w.welfare_code = u.welfare_code

WHERE u.user_role = 'USER'
  AND u.user_status = 'ACTIVE'
  AND u.plan_code IS NOT NULL
ORDER BY u.user_id;
