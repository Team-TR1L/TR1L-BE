-- 조합 데이터셋 생성기 (D3, D4)
-- psql 변수 seed, users 가 필요하다. 같은 seed 면 같은 데이터가 나온다
-- 차원마다 hashtextextended(seq, seed) 로 값을 따로 뽑아 조건끼리 겹치지 않게 한다
--   사용량    미만 50%, 같음 10%, 초과 30%, 무제한 요금제 10%
--   약정      없음 60%, 유효 20%, 기준 월 안에서 끝남 10%, 만료 10%
--   군인      없음 70%, 유효 10%, 기준 월 안에서 끝남 10%, 만료 10%
--   복지      해당 없음 60%, WLF-02~05 40%
--   옵션      0개 30%, 1개 30%, 2~3개 20%, 4~7개 10%, 월 중간 해지 포함 10%
--   사용자    활성 85%, 탈퇴 5%, 정지 5%, 요금제 없음 5%
--   user_id   앞 70%는 1부터 연속, 뒤 30%는 5,000,000 뒤로 13씩 건너뛴다
BEGIN;

CREATE TEMP TABLE gen_users AS
WITH hashed AS (
    SELECT
        gs AS seq,
        CASE
            WHEN gs <= (:users * 7) / 10 THEN gs::bigint
            ELSE 5000000 + gs::bigint * 13
        END AS user_id,
        ((hashtextextended(gs || ':usage', :seed) % 10) + 10) % 10 AS h_usage,
        ((hashtextextended(gs || ':plan', :seed) % 6) + 6) % 6 AS h_plan,
        ((hashtextextended(gs || ':contract', :seed) % 10) + 10) % 10 AS h_contract,
        ((hashtextextended(gs || ':soldier', :seed) % 10) + 10) % 10 AS h_soldier,
        ((hashtextextended(gs || ':welfare', :seed) % 10) + 10) % 10 AS h_welfare,
        ((hashtextextended(gs || ':welfare-kind', :seed) % 4) + 4) % 4 AS h_welfare_kind,
        ((hashtextextended(gs || ':options', :seed) % 10) + 10) % 10 AS h_options,
        ((hashtextextended(gs || ':status', :seed) % 20) + 20) % 20 AS h_status,
        ((hashtextextended(gs || ':amount', :seed) % 9) + 9) % 9 AS h_amount
    FROM generate_series(1, :users) AS gs
)
SELECT
    seq,
    user_id,
    CASE h_status WHEN 0 THEN 'WITHDRAWN' WHEN 1 THEN 'BANNED' ELSE 'ACTIVE' END AS user_status,
    CASE
        WHEN h_status = 2 THEN NULL
        WHEN h_usage = 9 THEN
            (ARRAY['PLN-001','PLN-002','PLN-005','PLN-011','PLN-018','PLN-050'])[h_plan + 1]
        -- 군인 기간이 있으면 군인 할인이 되는 요금제를 쓴다
        WHEN h_soldier >= 7 THEN 'PLN-043'
        ELSE (ARRAY['PLN-042','PLN-043','PLN-045','PLN-046','PLN-047','PLN-048'])[h_plan + 1]
    END AS plan_code,
    CASE
        WHEN h_usage <= 4 THEN 'below'
        WHEN h_usage = 5 THEN 'exactly'
        WHEN h_usage <= 8 THEN 'over'
        ELSE 'unlimited'
    END AS usage_kind,
    CASE
        WHEN h_contract <= 5 THEN 'none'
        WHEN h_contract <= 7 THEN 'active'
        WHEN h_contract = 8 THEN 'ends_in_month'
        ELSE 'expired'
    END AS contract_kind,
    CASE
        WHEN h_soldier <= 6 THEN 'none'
        WHEN h_soldier = 7 THEN 'active'
        WHEN h_soldier = 8 THEN 'ends_in_month'
        ELSE 'expired'
    END AS soldier_kind,
    CASE
        WHEN h_welfare <= 5 THEN 'WLF-01'
        ELSE (ARRAY['WLF-02','WLF-03','WLF-04','WLF-05'])[h_welfare_kind + 1]
    END AS welfare_code,
    CASE
        WHEN h_options <= 2 THEN 0
        WHEN h_options <= 5 THEN 1
        WHEN h_options <= 7 THEN 2 + (seq % 2)
        WHEN h_options = 8 THEN 4 + (seq % 4)
        ELSE -1
    END AS option_count,
    h_amount
FROM hashed;

-- users 본문 적재
INSERT INTO users (
    user_id, name, email, phone_number, birth_date, join_date, plan_code, welfare_code, is_welfare,
    user_role, user_status, from_time, to_time, day_time, created_at, modified_at
)
SELECT
    g.user_id,
    '테스트유저' || g.user_id,
    'job1-gen-' || g.user_id || '@example.com',
    '010-' || lpad(((g.seq - 1) / 10000)::text, 4, '0') || '-' || lpad(((g.seq - 1) % 10000)::text, 4, '0'),
    DATE '1985-01-01' + ((g.seq - 1) % 10000),
    DATE '2024-01-01' + ((g.seq - 1) % 365),
    g.plan_code,
    g.welfare_code,
    g.welfare_code <> 'WLF-01',
    'USER',
    g.user_status,
    '08',
    '22',
    lpad((((g.seq - 1) % 28) + 1)::text, 2, '0'),
    TIMESTAMPTZ '2025-01-01 00:00:00+09' + ((g.seq - 1) % 365) * INTERVAL '1 day',
    NOW()
FROM gen_users g;

-- 월별 usage 적재. 기준 월은 요금제 기본 제공량을 기준으로 정한다
INSERT INTO monthly_data_usage (user_id, usage_year_month, used_data_mb, usage_aggregated_at)
SELECT
    g.user_id,
    m.ym,
    CASE
        WHEN m.ym <> '2026-01' THEN 300 + ((g.seq - 1) % 4000)
        WHEN g.usage_kind = 'below' THEN (p.included_data_mb * (g.h_amount + 1)) / 10
        WHEN g.usage_kind = 'exactly' THEN p.included_data_mb
        WHEN g.usage_kind = 'over' THEN p.included_data_mb + 1 + ((g.seq - 1) % 3000)
        ELSE 500 + ((g.seq - 1) % 5000)
    END,
    m.aggregated_at
FROM gen_users g
JOIN plan p ON p.plan_code = g.plan_code
CROSS JOIN (
    VALUES ('2025-12', TIMESTAMPTZ '2025-12-31 23:59:59+09'),
           ('2026-01', TIMESTAMPTZ '2026-01-31 23:59:59+09'),
           ('2026-02', TIMESTAMPTZ '2026-02-28 23:59:59+09')
) AS m(ym, aggregated_at);

-- 약정 적재. 기간은 할인표가 있는 12개월, 24개월을 쓴다
INSERT INTO user_contract (user_id, start_date, end_date)
SELECT
    user_id,
    CASE contract_kind
        WHEN 'active' THEN DATE '2025-01-01'
        WHEN 'ends_in_month' THEN CASE WHEN seq % 2 = 0 THEN DATE '2024-01-01' ELSE DATE '2025-01-31' END
        ELSE DATE '2023-07-01'
    END,
    CASE contract_kind
        WHEN 'active' THEN DATE '2027-01-01'
        WHEN 'ends_in_month' THEN CASE WHEN seq % 2 = 0 THEN DATE '2026-01-01' ELSE DATE '2026-01-31' END
        ELSE DATE '2025-07-01'
    END
FROM gen_users
WHERE contract_kind <> 'none';

-- 군인 기간 적재
INSERT INTO user_soldier (user_id, start_date, end_date)
SELECT
    user_id,
    CASE soldier_kind WHEN 'active' THEN DATE '2025-08-01' ELSE DATE '2024-07-01' END,
    CASE soldier_kind
        WHEN 'active' THEN NULL
        WHEN 'ends_in_month' THEN CASE WHEN seq % 2 = 0 THEN DATE '2026-01-01' ELSE DATE '2026-01-31' END
        ELSE DATE '2025-12-31'
    END
FROM gen_users
WHERE soldier_kind <> 'none';

-- 옵션 구독 적재. 고르는 옵션도 seed 로 섞는다
-- 월 중간 해지 사용자(option_count = -1)는 3개를 넣는다
--   1번 정상 구독, 2번 1월 15일 해지, 3번 1월 1일 0시 해지
WITH ranked_options AS (
    SELECT
        g.user_id,
        g.option_count,
        os.option_service_code,
        row_number() OVER (
            PARTITION BY g.user_id
            ORDER BY hashtextextended(g.user_id || ':' || os.option_service_code, :seed)
        ) AS option_rank
    FROM gen_users g
    CROSS JOIN option_service os
    WHERE g.option_count <> 0
)
INSERT INTO user_option_subscription (user_id, option_service_code, start_date, end_date)
SELECT
    user_id,
    option_service_code,
    TIMESTAMPTZ '2025-12-01 00:00:00+09',
    CASE
        WHEN option_count = -1 AND option_rank = 2 THEN TIMESTAMPTZ '2026-01-15 00:00:00+09'
        WHEN option_count = -1 AND option_rank = 3 THEN TIMESTAMPTZ '2026-01-01 00:00:00+09'
        ELSE TIMESTAMPTZ '2026-12-31 23:59:59+09'
    END
FROM ranked_options
WHERE option_rank <= CASE WHEN option_count = -1 THEN 3 ELSE option_count END;

COMMIT;
