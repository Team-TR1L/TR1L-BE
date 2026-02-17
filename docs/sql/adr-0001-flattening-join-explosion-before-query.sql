WITH
/* ---------------------------
   0) Parameters / Period
   --------------------------- */
params AS (
  SELECT
    :userId::bigint              AS user_id,
    :yearMonth::varchar(7)       AS usage_year_month,

    to_date(:yearMonth || '-01', 'YYYY-MM-DD') AS month_start_date,
    (to_date(:yearMonth || '-01', 'YYYY-MM-DD') + INTERVAL '1 month' - INTERVAL '1 day')::date AS month_end_date,

    /* timestamptz 기간(half-open): [start, end+1day) */
    (to_date(:yearMonth || '-01', 'YYYY-MM-DD')::timestamptz) AS month_start_ts,
    ((to_date(:yearMonth || '-01', 'YYYY-MM-DD') + INTERVAL '1 month')::timestamptz) AS month_end_exclusive_ts
),

/* ---------------------------
   1) User
   --------------------------- */
user_base AS (
  SELECT
    u.user_id,
    u.name,
    u.birth_date,
    u.email,
    u.phone_number,
    u.plan_code                AS user_current_plan_code,
    u.welfare_code,
    u.is_welfare,
    u.user_role,
    u.user_status,
    u.from_time,
    u.to_time,
    u.day_time,
    u.join_date,
    u.created_at,
    u.modified_at
  FROM users u
  JOIN params p ON p.user_id = u.user_id
),

/* ---------------------------
   2) Plan selection
   --------------------------- */
effective_plan AS (
  SELECT
    ub.user_id,
    COALESCE(up.plan_code, ub.user_current_plan_code) AS plan_code,

    /* 참고용: 선택된 요금제 이력의 기간(없으면 NULL) */
    up.start_date AS plan_start_date,
    up.end_date   AS plan_end_date
  FROM user_base ub
  JOIN params p ON p.user_id = ub.user_id
  LEFT JOIN LATERAL (
    SELECT
      up.plan_code,
      up.start_date,
      up.end_date
    FROM user_plans up
    WHERE up.user_id = ub.user_id
      AND up.start_date < p.month_end_exclusive_ts
      AND up.end_date   >= p.month_start_ts
    ORDER BY up.start_date DESC
    LIMIT 1
  ) up ON TRUE
),

/* ---------------------------
   3) Plan fact
   --------------------------- */
plan_fact AS (
  SELECT
    ep.user_id,

    pl.plan_code,
    pl.name               AS plan_name,
    pl.info               AS plan_info,

    pl.monthly_price      AS plan_monthly_price,
    pl.included_data_mb   AS included_data_mb,
    pl.excess_charge_per_mb AS excess_charge_per_mb,

    pl.is_military_eligible AS is_military_eligible,

    pl.network_type_code,
    nt.network_type_name,

    pl.data_billing_type_code,
    dbt.data_billing_type_name
  FROM effective_plan ep
  JOIN plan pl
    ON pl.plan_code = ep.plan_code
  JOIN network_type nt
    ON nt.network_type_code = pl.network_type_code
  JOIN data_billing_type dbt
    ON dbt.data_billing_type_code = pl.data_billing_type_code
),

/* ---------------------------
   4) Welfare fact
   --------------------------- */
welfare_fact AS (
  SELECT
    ub.user_id,
    ub.welfare_code,
    COALESCE(w.welfare_name, '일반') AS welfare_name,
    COALESCE(w.discount_rate, 0)     AS welfare_rate,
    w.max_discount                   AS welfare_max_discount
  FROM user_base ub
  LEFT JOIN welfare_discount w
    ON w.welfare_code = ub.welfare_code
),

/* ---------------------------
   5) Monthly Usage
   --------------------------- */
usage_fact AS (
  SELECT
    p.user_id,
    COALESCE(mdu.used_data_mb, 0) AS used_data_mb,
    mdu.usage_aggregated_at       AS usage_aggregated_at
  FROM params p
  LEFT JOIN monthly_data_usage mdu
    ON mdu.user_id = p.user_id
   AND mdu.usage_year_month = p.usage_year_month
),

/* ---------------------------
   6) Included options
   --------------------------- */
included_options_fact AS (
  SELECT
    pf.user_id,
    COALESCE(
      jsonb_agg(
        jsonb_build_object(
          'optionServiceCode', os.option_service_code,
          'optionServiceName', os.option_service_name,
          'monthlyPrice',      os.monthly_price
        )
        ORDER BY os.option_service_code
      ) FILTER (WHERE os.option_service_code IS NOT NULL),
      '[]'::jsonb
    ) AS included_options_jsonb
  FROM plan_fact pf
  LEFT JOIN plan_included_option pio
    ON pio.plan_code = pf.plan_code
  LEFT JOIN option_service os
    ON os.option_service_code = pio.option_service_code
  GROUP BY pf.user_id
),

/* ---------------------------
   7) Subscribed options
   --------------------------- */
subscribed_options_fact AS (
  SELECT
    p.user_id,

    COALESCE(
      jsonb_agg(
        jsonb_build_object(
          'optionServiceCode', os.option_service_code,
          'optionServiceName', os.option_service_name,
          'monthlyPrice',      os.monthly_price,
          'startDate',         uos.start_date,
          'endDate',           uos.end_date
        )
        ORDER BY os.option_service_code
      ) FILTER (WHERE os.option_service_code IS NOT NULL),
      '[]'::jsonb
    ) AS subscribed_options_jsonb,

    COALESCE(SUM(os.monthly_price), 0) AS subscribed_options_total_price

  FROM params p
  LEFT JOIN user_option_subscription uos
    ON uos.user_id = p.user_id
   AND uos.start_date < p.month_end_exclusive_ts
   AND uos.end_date   >= p.month_start_ts
  LEFT JOIN option_service os
    ON os.option_service_code = uos.option_service_code
  GROUP BY p.user_id
),

/* ---------------------------
   8) Contract
   --------------------------- */
contract_raw AS (
  SELECT
    p.user_id,
    uc.start_date AS contract_start_date,
    uc.end_date   AS contract_end_date,

    (
      EXTRACT(YEAR  FROM age(uc.end_date, uc.start_date))::int * 12
      + EXTRACT(MONTH FROM age(uc.end_date, uc.start_date))::int
    ) AS contract_duration_months_raw

  FROM params p
  LEFT JOIN LATERAL (
    SELECT
      uc.start_date,
      uc.end_date
    FROM user_contract uc
    WHERE uc.user_id = p.user_id
      AND uc.start_date <= p.month_end_date
      AND uc.end_date   >= p.month_start_date
    ORDER BY uc.start_date DESC
    LIMIT 1
  ) uc ON TRUE
),

contract_fact AS (
  SELECT
    cr.user_id,
    cr.contract_start_date,
    cr.contract_end_date,
    cr.contract_duration_months_raw,

    cd.duration_months     AS contract_policy_duration_months,
    cd.discount_percent    AS contract_discount_percent
  FROM contract_raw cr
  LEFT JOIN LATERAL (
    SELECT
      cd.duration_months,
      cd.discount_percent
    FROM contract_discount cd
    WHERE cr.contract_duration_months_raw IS NOT NULL
      AND cd.duration_months <= cr.contract_duration_months_raw
    ORDER BY cd.duration_months DESC
    LIMIT 1
  ) cd ON TRUE
),

/* ---------------------------
   9) Soldier
   --------------------------- */
soldier_fact AS (
  SELECT
    p.user_id,
    us.start_date AS soldier_start_date,
    us.end_date   AS soldier_end_date,
    CASE
      WHEN us.user_id IS NULL THEN FALSE
      WHEN us.start_date <= p.month_end_date
       AND (us.end_date IS NULL OR us.end_date >= p.month_start_date)
      THEN TRUE
      ELSE FALSE
    END AS is_soldier_active_in_month
  FROM params p
  LEFT JOIN user_soldier us
    ON us.user_id = p.user_id
)

/* =========================================================
    Projection
   ========================================================= */
SELECT
  /* period */
  p.usage_year_month,
  p.month_start_date,
  p.month_end_date,

  /* user */
  ub.user_id,
  ub.name         AS user_name,
  ub.birth_date   AS user_birth_date,
  ub.email        AS recipient_email,
  ub.phone_number AS recipient_phone,

  ub.user_role,
  ub.user_status,
  ub.join_date,

  /* policy */
  ub.from_time,
  ub.to_time,
  ub.day_time,

  /* plan */
  pf.plan_code,
  pf.plan_name,
  pf.plan_info,
  pf.plan_monthly_price,
  pf.included_data_mb,
  pf.excess_charge_per_mb,
  pf.network_type_code,
  pf.network_type_name,
  pf.data_billing_type_code,
  pf.data_billing_type_name,
  pf.is_military_eligible,

  /* plan */
  ep.plan_start_date AS effective_plan_start_ts,
  ep.plan_end_date   AS effective_plan_end_ts,

  /* usage */
  uf.used_data_mb,
  uf.usage_aggregated_at,

  /* excess usage */
  GREATEST(uf.used_data_mb - pf.included_data_mb, 0) AS excess_data_mb,
  (GREATEST(uf.used_data_mb - pf.included_data_mb, 0) * pf.excess_charge_per_mb) AS excess_charge_amount,

  /* welfare */
  wf.welfare_code,
  wf.welfare_name,
  wf.welfare_rate,
  wf.welfare_max_discount,

  /* contract */
  cf.contract_start_date,
  cf.contract_end_date,
  cf.contract_duration_months_raw,
  cf.contract_policy_duration_months,
  COALESCE(cf.contract_discount_percent, 0) AS contract_discount_percent,

  /* soldier */
  sf.soldier_start_date,
  sf.soldier_end_date,
  sf.is_soldier_active_in_month,

  /* options */
  iof.included_options_jsonb,
  sof.subscribed_options_jsonb,
  sof.subscribed_options_total_price,


  jsonb_build_object(
    'welfare', jsonb_build_object(
      'code', wf.welfare_code,
      'name', wf.welfare_name,
      'rate', wf.welfare_rate,
      'maxDiscount', wf.welfare_max_discount
    ),
    'contract', jsonb_build_object(
      'startDate', cf.contract_start_date,
      'endDate', cf.contract_end_date,
      'durationMonthsRaw', cf.contract_duration_months_raw,
      'policyDurationMonths', cf.contract_policy_duration_months,
      'discountPercent', COALESCE(cf.contract_discount_percent, 0)
    ),
    'soldier', jsonb_build_object(
      'activeInMonth', sf.is_soldier_active_in_month,
      'startDate', sf.soldier_start_date,
      'endDate', sf.soldier_end_date,
      'planEligible', pf.is_military_eligible
    )
  ) AS policies_jsonb

FROM params p
JOIN user_base ub ON ub.user_id = p.user_id
JOIN effective_plan ep ON ep.user_id = p.user_id
JOIN plan_fact pf ON pf.user_id = p.user_id
JOIN welfare_fact wf ON wf.user_id = p.user_id
JOIN usage_fact uf ON uf.user_id = p.user_id
LEFT JOIN included_options_fact iof ON iof.user_id = p.user_id
LEFT JOIN subscribed_options_fact sof ON sof.user_id = p.user_id
LEFT JOIN contract_fact cf ON cf.user_id = p.user_id
LEFT JOIN soldier_fact sf ON sf.user_id = p.user_id;
