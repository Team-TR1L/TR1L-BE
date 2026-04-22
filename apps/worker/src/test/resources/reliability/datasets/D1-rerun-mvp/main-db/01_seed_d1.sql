BEGIN;

-- D1 전체 사용자 번호표 생성
-- user 상태와 plan 분포 같이 고정
CREATE TEMP TABLE d1_numbered_users AS
SELECT
    gs AS seq,
    gs::bigint AS user_id,
    CASE
        WHEN gs <= 28500 THEN 'ACTIVE'
        WHEN gs <= 29400 THEN 'WITHDRAWN'
        WHEN gs <= 29700 THEN 'BANNED'
        ELSE 'ACTIVE'
    END AS user_status,
    CASE
        WHEN gs <= 28500 THEN
            (ARRAY['PLN-001','PLN-002','PLN-005','PLN-011','PLN-018','PLN-043','PLN-050'])[((gs - 1) % 7) + 1]
        WHEN gs <= 29700 THEN
            (ARRAY['PLN-001','PLN-002','PLN-005'])[((gs - 1) % 3) + 1]
        ELSE NULL
    END AS plan_code,
    CASE
        WHEN gs <= 5700 THEN
            (ARRAY['WLF-02','WLF-03','WLF-04','WLF-05'])[((gs - 1) % 4) + 1]
        ELSE 'WLF-01'
    END AS welfare_code
FROM generate_series(1, 30000) AS gs;

-- users 본문 적재
-- Step1 대상 필터와 plan 분기 확인용
INSERT INTO users (
    user_id,
    name,
    email,
    phone_number,
    birth_date,
    join_date,
    plan_code,
    welfare_code,
    is_welfare,
    user_role,
    user_status,
    from_time,
    to_time,
    day_time,
    created_at,
    modified_at
)
SELECT
    nu.user_id,
    '테스트유저' || nu.user_id,
    'job1-d1-' || nu.user_id || '@example.com',
    '010-' || lpad(((nu.seq - 1) / 10000)::text, 4, '0') || '-' || lpad(((nu.seq - 1) % 10000)::text, 4, '0'),
    DATE '1985-01-01' + ((nu.seq - 1) % 10000),
    DATE '2024-01-01' + ((nu.seq - 1) % 365),
    nu.plan_code,
    nu.welfare_code,
    nu.welfare_code <> 'WLF-01',
    'USER',
    nu.user_status,
    '08',
    '22',
    lpad((((nu.seq - 1) % 28) + 1)::text, 2, '0'),
    TIMESTAMPTZ '2025-01-01 00:00:00+09' + ((nu.seq - 1) % 365) * INTERVAL '1 day',
    NOW()
FROM d1_numbered_users nu;

-- 월별 usage 적재
-- 기준 월과 앞뒤 noise 월 같이 주입
INSERT INTO monthly_data_usage (
    user_id,
    usage_year_month,
    used_data_mb,
    usage_aggregated_at
)
SELECT
    user_id,
    ym,
    CASE
        WHEN ym = '2026-01' THEN 500 + ((seq - 1) % 5000)
        WHEN ym = '2025-12' THEN 300 + ((seq - 1) % 4000)
        ELSE 700 + ((seq - 1) % 4500)
    END,
    CASE
        WHEN ym = '2026-01' THEN TIMESTAMPTZ '2026-01-31 23:59:59+09'
        WHEN ym = '2025-12' THEN TIMESTAMPTZ '2025-12-31 23:59:59+09'
        ELSE TIMESTAMPTZ '2026-02-28 23:59:59+09'
    END
FROM (
    SELECT seq, user_id
    FROM d1_numbered_users
    WHERE seq <= 28500
) eligible
CROSS JOIN (
    VALUES ('2025-12'), ('2026-01'), ('2026-02')
) months(ym);

-- active contract 적재
-- 기준 월 안에서 유효한 계약 구간
INSERT INTO user_contract (
    user_id,
    start_date,
    end_date
)
SELECT
    user_id,
    DATE '2025-01-01',
    DATE '2027-01-01'
FROM d1_numbered_users
WHERE seq BETWEEN 1 AND 8550;

-- expired contract 적재
-- 기준 월 이전 종료 구간
INSERT INTO user_contract (
    user_id,
    start_date,
    end_date
)
SELECT
    user_id,
    DATE '2024-01-01',
    DATE '2025-06-30'
FROM d1_numbered_users
WHERE seq BETWEEN 8551 AND 9975;

-- active soldier 적재
-- 기준 월 할인 적용 대상
INSERT INTO user_soldier (
    user_id,
    start_date,
    end_date
)
SELECT
    user_id,
    DATE '2025-08-01',
    NULL
FROM d1_numbered_users
WHERE seq BETWEEN 1 AND 1425;

-- expired soldier 적재
-- 기준 월 제외 확인용
INSERT INTO user_soldier (
    user_id,
    start_date,
    end_date
)
SELECT
    user_id,
    DATE '2024-01-01',
    DATE '2025-11-30'
FROM d1_numbered_users
WHERE seq BETWEEN 1426 AND 1710;

-- 옵션 개수 분포 계산
-- 1개 2개 3개 4개 구간 고정
WITH option_counts AS (
    SELECT
        seq,
        user_id,
        CASE
            WHEN seq <= 8550 THEN 1
            WHEN seq <= 17100 THEN 2
            WHEN seq <= 25650 THEN 3
            WHEN seq <= 28500 THEN 4
            ELSE 0
        END AS option_count
    FROM d1_numbered_users
    WHERE seq <= 28500
),
ranked_options AS (
    SELECT
        oc.user_id,
        os.option_service_code,
        row_number() OVER (PARTITION BY oc.user_id ORDER BY os.option_service_code) AS option_rank,
        oc.option_count
    FROM option_counts oc
    CROSS JOIN option_service os
)
-- 옵션 구독 적재
-- Step1 포함 옵션 조회 입력값
INSERT INTO user_option_subscription (
    user_id,
    option_service_code,
    start_date,
    end_date
)
SELECT
    user_id,
    option_service_code,
    TIMESTAMPTZ '2025-12-01 00:00:00+09',
    TIMESTAMPTZ '2026-12-31 23:59:59+09'
FROM ranked_options
WHERE option_rank <= option_count;

-- D1 입력 적재 마무리
COMMIT;
