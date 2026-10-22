-- D2 경계값 데이터셋
-- 경우마다 사용자 10명. user_id 는 경우 번호 * 1000 + 1~10, 큰 user_id 경우만 9,000,000,001
-- 기본값은 PLN-047(500MB, 24,750원), 복지 WLF-01, 사용량 100MB, 약정·군인·옵션 없음
-- 경우별 기대 결과는 dataset.yml 의 boundaryCases 에 있다
BEGIN;

CREATE TEMP TABLE d2_cases (
    case_no        int PRIMARY KEY,
    code           text NOT NULL,
    plan_code      text,
    welfare_code   text NOT NULL DEFAULT 'WLF-01',
    user_status    text NOT NULL DEFAULT 'ACTIVE',
    user_role      text NOT NULL DEFAULT 'USER',
    usage_mb       bigint,
    usage_late     boolean NOT NULL DEFAULT false,
    contract_start date,
    contract_end   date,
    soldier_start  date,
    soldier_end    date,
    option_start   timestamptz,
    option_end     timestamptz
);

INSERT INTO d2_cases (case_no, code, plan_code, welfare_code, user_status, user_role, usage_mb, usage_late,
                      contract_start, contract_end, soldier_start, soldier_end, option_start, option_end)
VALUES
    -- 사용량과 기본 제공량
    (1,  'USAGE_EQUAL_INCLUDED',        'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 500,    false, NULL, NULL, NULL, NULL, NULL, NULL),
    (2,  'USAGE_INCLUDED_PLUS_1MB',     'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 501,    false, NULL, NULL, NULL, NULL, NULL, NULL),
    (3,  'USAGE_INCLUDED_MINUS_1MB',    'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 499,    false, NULL, NULL, NULL, NULL, NULL, NULL),
    (4,  'USAGE_ZERO',                  'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 0,      false, NULL, NULL, NULL, NULL, NULL, NULL),
    (5,  'USAGE_OVER_3GB',              'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100500, false, NULL, NULL, NULL, NULL, NULL, NULL),
    (6,  'DATA_BLOCKED_PLAN_OVER',      'PLN-058', 'WLF-01', 'ACTIVE', 'USER', 1000,   false, NULL, NULL, NULL, NULL, NULL, NULL),
    (7,  'NO_INCLUDED_DATA_PLAN',       'PLN-044', 'WLF-01', 'ACTIVE', 'USER', 1000,   false, NULL, NULL, NULL, NULL, NULL, NULL),
    (8,  'USAGE_ROW_MISSING',           'PLN-047', 'WLF-01', 'ACTIVE', 'USER', NULL,   false, NULL, NULL, NULL, NULL, NULL, NULL),
    (9,  'USAGE_AGGREGATED_AFTER_CUTOFF','PLN-047','WLF-01', 'ACTIVE', 'USER', 800,    true,  NULL, NULL, NULL, NULL, NULL, NULL),
    -- 약정 기간
    (10, 'CONTRACT_ENDS_MONTH_START',   'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, DATE '2024-01-01', DATE '2026-01-01', NULL, NULL, NULL, NULL),
    (11, 'CONTRACT_ENDS_MONTH_END',     'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, DATE '2025-01-31', DATE '2026-01-31', NULL, NULL, NULL, NULL),
    (12, 'CONTRACT_STARTS_MONTH_END',   'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, DATE '2026-01-31', DATE '2028-01-31', NULL, NULL, NULL, NULL),
    (13, 'CONTRACT_STARTS_NEXT_MONTH',  'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, DATE '2026-02-01', DATE '2028-02-01', NULL, NULL, NULL, NULL),
    (14, 'CONTRACT_18_MONTHS',          'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, DATE '2025-01-01', DATE '2026-07-01', NULL, NULL, NULL, NULL),
    (15, 'CONTRACT_ENDED_PREV_MONTH',   'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, DATE '2023-12-31', DATE '2025-12-31', NULL, NULL, NULL, NULL),
    -- 군인 기간
    (16, 'SOLDIER_ENDS_MONTH_START',    'PLN-043', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, DATE '2024-07-01', DATE '2026-01-01', NULL, NULL),
    (17, 'SOLDIER_ENDED_PREV_MONTH',    'PLN-043', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, DATE '2024-07-01', DATE '2025-12-31', NULL, NULL),
    (18, 'SOLDIER_STARTS_MONTH_END',    'PLN-043', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, DATE '2026-01-31', NULL, NULL, NULL),
    (19, 'SOLDIER_STARTS_NEXT_MONTH',   'PLN-043', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, DATE '2026-02-01', NULL, NULL, NULL),
    (20, 'SOLDIER_ON_NON_MILITARY_PLAN','PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, DATE '2025-08-01', NULL, NULL, NULL),
    -- 옵션 구독 기간
    (21, 'OPTION_ENDS_MONTH_START',     'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, NULL, NULL,
         TIMESTAMPTZ '2025-12-01 00:00:00+09', TIMESTAMPTZ '2026-01-01 00:00:00+09'),
    (22, 'OPTION_STARTS_MONTH_LAST_SEC','PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, NULL, NULL,
         TIMESTAMPTZ '2026-01-31 23:59:59+09', TIMESTAMPTZ '2026-12-31 23:59:59+09'),
    (23, 'OPTION_STARTS_NEXT_MONTH',    'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, NULL, NULL,
         TIMESTAMPTZ '2026-02-01 00:00:00+09', TIMESTAMPTZ '2026-12-31 23:59:59+09'),
    (24, 'OPTION_ENDS_MID_MONTH',       'PLN-047', 'WLF-01', 'ACTIVE', 'USER', 100, false, NULL, NULL, NULL, NULL,
         TIMESTAMPTZ '2025-12-01 00:00:00+09', TIMESTAMPTZ '2026-01-15 00:00:00+09'),
    -- 복지 할인 상한
    (25, 'WELFARE_CAP_HIT',             'PLN-001', 'WLF-05', 'ACTIVE', 'USER', 100,  false, NULL, NULL, NULL, NULL, NULL, NULL),
    (26, 'WELFARE_UNDER_CAP',           'PLN-045', 'WLF-05', 'ACTIVE', 'USER', 100,  false, NULL, NULL, NULL, NULL, NULL, NULL),
    (27, 'WELFARE_BASE_WITH_OVERAGE',   'PLN-047', 'WLF-02', 'ACTIVE', 'USER', 4500, false, NULL, NULL, NULL, NULL, NULL, NULL),
    (28, 'ALL_DISCOUNTS',               'PLN-043', 'WLF-04', 'ACTIVE', 'USER', 100,  false, DATE '2025-01-01', DATE '2027-01-01', DATE '2025-08-01', NULL, NULL, NULL),
    -- user_id 와 대상 필터
    (29, 'USER_ID_VERY_LARGE',          'PLN-047', 'WLF-01', 'ACTIVE',    'USER',  100, false, NULL, NULL, NULL, NULL, NULL, NULL),
    (30, 'ACTIVE_WITHOUT_PLAN',         NULL,      'WLF-01', 'ACTIVE',    'USER',  100, false, NULL, NULL, NULL, NULL, NULL, NULL),
    (31, 'WITHDRAWN_USER',              'PLN-047', 'WLF-01', 'WITHDRAWN', 'USER',  100, false, NULL, NULL, NULL, NULL, NULL, NULL),
    (32, 'BANNED_USER',                 'PLN-047', 'WLF-01', 'BANNED',    'USER',  100, false, NULL, NULL, NULL, NULL, NULL, NULL),
    (33, 'ADMIN_ROLE',                  'PLN-047', 'WLF-01', 'ACTIVE',    'ADMIN', 100, false, NULL, NULL, NULL, NULL, NULL, NULL);

CREATE TEMP TABLE d2_users AS
SELECT
    c.*,
    CASE WHEN c.code = 'USER_ID_VERY_LARGE' THEN 9000000000 + n ELSE c.case_no * 1000 + n END::bigint AS user_id,
    n
FROM d2_cases c
CROSS JOIN generate_series(1, 10) AS n;

INSERT INTO users (
    user_id, name, email, phone_number, birth_date, join_date, plan_code, welfare_code, is_welfare,
    user_role, user_status, from_time, to_time, day_time, created_at, modified_at
)
SELECT
    user_id,
    '경계값' || code || '-' || n,
    'job1-d2-' || user_id || '@example.com',
    '010-' || lpad((case_no)::text, 4, '0') || '-' || lpad(n::text, 4, '0'),
    DATE '1990-01-01' + n,
    DATE '2024-01-01',
    plan_code,
    welfare_code,
    welfare_code <> 'WLF-01',
    user_role,
    user_status,
    '08',
    '22',
    '15',
    TIMESTAMPTZ '2025-01-01 00:00:00+09',
    NOW()
FROM d2_users;

-- 기준 월 사용량. 앞뒤 달 noise 도 같이 넣는다
INSERT INTO monthly_data_usage (user_id, usage_year_month, used_data_mb, usage_aggregated_at)
SELECT user_id, '2026-01', usage_mb,
       CASE WHEN usage_late THEN TIMESTAMPTZ '2026-02-10 09:00:00+09' ELSE TIMESTAMPTZ '2026-01-31 23:59:59+09' END
FROM d2_users
WHERE usage_mb IS NOT NULL
UNION ALL
SELECT user_id, '2025-12', 9999, TIMESTAMPTZ '2025-12-31 23:59:59+09' FROM d2_users
UNION ALL
SELECT user_id, '2026-02', 9999, TIMESTAMPTZ '2026-02-28 23:59:59+09' FROM d2_users;

INSERT INTO user_contract (user_id, start_date, end_date)
SELECT user_id, contract_start, contract_end FROM d2_users WHERE contract_start IS NOT NULL;

INSERT INTO user_soldier (user_id, start_date, end_date)
SELECT user_id, soldier_start, soldier_end FROM d2_users WHERE soldier_start IS NOT NULL;

-- 옵션은 OPT 코드 가장 앞의 1개
INSERT INTO user_option_subscription (user_id, option_service_code, start_date, end_date)
SELECT u.user_id, (SELECT min(option_service_code) FROM option_service), u.option_start, u.option_end
FROM d2_users u
WHERE u.option_start IS NOT NULL;

COMMIT;
