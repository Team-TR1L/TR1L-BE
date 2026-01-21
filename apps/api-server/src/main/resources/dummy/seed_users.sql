BEGIN;
WITH base AS (
    SELECT COALESCE(
        MAX(right(regexp_replace(phone_number, '\D', '', 'g'), 8)::int),
        0
    ) AS max_phone_seq
    FROM users
    WHERE phone_number ~ '^010-\d{4}-\d{4}$'
),
user_data AS (
    SELECT
        generate_tsid(s.i) AS user_id,
        (ARRAY['김','이','박','최','정','강','조','윤','장','임','한','오','서','신','권','황','안','송','전','홍'])[floor(random()*20)+1] ||
        (ARRAY['민준','서준','도윤','예준','시우','하준','주원','지호','지후','준우','서연','서윤','지우','서현','하은','하윤','민서','지유','윤서','채원'])[floor(random()*20)+1] AS name,
        'user_' || s.i || '@email.com' AS email,

        -- ✅ 여기만 바뀜: 랜덤 제거 + 유니크 보장
        '010-' ||
        lpad(((base.max_phone_seq + s.i) / 10000)::text, 4, '0') || '-' ||
        lpad(((base.max_phone_seq + s.i) % 10000)::text, 4, '0') AS phone_number,

        DATE '1960-01-01' + (random() * (DATE '2010-12-31' - DATE '1960-01-01'))::int AS birth_date,
        DATE '2015-01-01' + (random() * (DATE '2024-12-31' - DATE '2015-01-01'))::int AS join_date,
        (ARRAY['PLN-001','PLN-002','PLN-005','PLN-011','PLN-018','PLN-043','PLN-050'])[floor(random()*7)+1] AS plan_code,
        (ARRAY['WLF-01','WLF-02','WLF-03','WLF-04','WLF-05'])[floor(random()*5)+1] AS welfare_code,
        'USER' AS user_role,
        'ACTIVE' AS user_status,
        ft.f_time,
        (ft.f_time + floor(random()*2)::int + 1) AS t_time,
        NOW() - (random() * interval '1 year') AS created_at
    FROM generate_series(1, 100000) AS s(i)
    CROSS JOIN base
    CROSS JOIN LATERAL (SELECT floor(random()*21)::int AS f_time) AS ft
)
INSERT
INTO users (user_id, name, email, phone_number, birth_date, join_date,
            plan_code, welfare_code, is_welfare,
            user_role, user_status,
            from_time, to_time, day_time,
            created_at, modified_at)
SELECT user_id,
       name,
       email,
       phone_number,
       birth_date,
       join_date,
       plan_code,
       welfare_code,
       (welfare_code != 'WLF-01') AS is_welfare,
       user_role,
       user_status,
       lpad(f_time::text, 2, '0'),
       lpad(t_time::text, 2, '0'),
       lpad((1 + floor(random() * 28))::int::text, 2, '0'),
       created_at,
       NOW()
FROM user_data;

-- 3. 유저별 부가 서비스 랜덤 구독 (최대 7개)
-- 모든 유저(10만)와 부가서비스(16개)를 Join 후 유저별로 0~7개를 무작위 추출
INSERT INTO user_option_subscription (user_id, option_service_code, start_date, end_date)
SELECT user_id,
       option_service_code,
       created_at                            AS start_date, -- 유저 생성 시점부터 구독 시작으로 설정
       '2026-12-31 23:59:59+09'::timestamptz AS end_date
FROM (SELECT u.user_id,
             u.created_at,
             o.option_service_code,
             row_number() OVER (PARTITION BY u.user_id ORDER BY random()) as rank,
             floor(random() * 8)                                          as target_count -- 0~7개 사이의 랜덤한 목표 개수 설정
      FROM users u
               CROSS JOIN option_service o) sub
WHERE rank <= target_count;

COMMIT;




