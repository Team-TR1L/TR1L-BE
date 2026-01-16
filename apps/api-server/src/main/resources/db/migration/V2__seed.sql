/*
 * =================================================================
 *                       DB 초기 데이터 셋업
 * ================================================================
 */

/* 부가 서비스 종류 초기 데이터 */
INSERT INTO option_service (option_service_code, option_service_name)
VALUES
    ('OPT-0001', 'V컬러링'),
    ('OPT-0002', '디즈니+'),
    ('OPT-0003', '넷플릭스'),
    ('OPT-0004', '요기요'),
    ('OPT-0005', 'GS칼텍스 주유세차권&차량정비서비스'),
    ('OPT-0006', '티빙'),
    ('OPT-0007', '스노우 VIP'),
    ('OPT-0008', '리디셀렉트 도서멤버쉽'),
    ('OPT-0009', '예스24 크레마 클럽'),
    ('OPT-0010', '모바일 한경'),
    ('OPT-0011', '클래스 101+'),
    ('OPT-0012', '밀리의 서재'),
    ('OPT-0013', '유튜브 프리미엄'),
    ('OPT-0014', '아이들나라(스탠다드+러닝)'),
    ('OPT-0015', '지니뮤직(genie)'),
    ('OPT-0016', '구글 원(Google One)')
ON CONFLICT (option_service_code) DO NOTHING;


/* 부가 서비스 가격 정책 */
/* option_policy_history: 2025~2026 (24개월 x 16개) */
WITH services AS (
    SELECT *
    FROM (VALUES
        ('OPT-0001',  3135::bigint),
        ('OPT-0002',  9405::bigint),
        ('OPT-0003',  6500::bigint),
        ('OPT-0004',  6000::bigint),
        ('OPT-0005',  5900::bigint),
        ('OPT-0006',  4950::bigint),
        ('OPT-0007',  4000::bigint),
        ('OPT-0008',  3900::bigint),
        ('OPT-0009',  4500::bigint),
        ('OPT-0010',  3300::bigint),
        ('OPT-0011', 19900::bigint),
        ('OPT-0012', 11900::bigint),
        ('OPT-0013', 13900::bigint),
        ('OPT-0014',     0::bigint),
        ('OPT-0015',     0::bigint),
        ('OPT-0016',     0::bigint)
    ) AS v(option_service_code, monthly_price)
),
months AS (
    SELECT gs::date AS month_start
    FROM generate_series(
        DATE '2025-01-01',
        DATE '2026-12-01',
        INTERVAL '1 month'
    ) AS gs
),
ranges AS (
    SELECT
        m.month_start,
        (date_trunc('month', m.month_start::timestamp) + INTERVAL '1 month' - INTERVAL '1 day')::date AS month_end
    FROM months m
),
to_insert AS (
    SELECT
        s.option_service_code,
        s.monthly_price,
        (r.month_start::timestamp AT TIME ZONE 'Asia/Seoul') AS effective_from,
        ((r.month_end::timestamp + TIME '23:59:59') AT TIME ZONE 'Asia/Seoul') AS effective_to
    FROM services s
    CROSS JOIN ranges r
)
INSERT INTO option_policy_history (option_service_code, monthly_price, effective_from, effective_to)
SELECT
    t.option_service_code,
    t.monthly_price,
    t.effective_from,
    t.effective_to
FROM to_insert t
ON CONFLICT (option_service_code, effective_from) DO NOTHING;

/* 복지 유형 */
INSERT INTO welfare_discount (discount_rate, max_discount, welfare_code, welfare_name)
VALUES
    (0,null,'WLF-01','일반'),
    (0.35,null,'WLF-02','장애인 할인'),
    (0.35,28600,'WLF-03','국가 유공자 할인'),
    (0.35,23650,'WLF-04','기초 생활 수급자 할인'),
    (0.50,12100,'WLF-05','기초 연금 수급자 할인')
ON CONFLICT (welfare_code) DO NOTHING;

/* 네트워크 유형 */
INSERT INTO network_type (network_type_code, network_type_name)
VALUES
    ('NET-01','5G'),
    ('NET-02','LTE')
ON CONFLICT (network_type_code) DO NOTHING;

/* 요금제 데이터 과금 타입 */
INSERT INTO data_billing_type (data_billing_type_code, data_billing_type_name)
VALUES
    ('DBT-01','완전 무제한'),
    ('DBT-02','nMB 이후 과금'),
    ('DBT-03','초과 없음')
ON CONFLICT (data_billing_type_code) DO NOTHING;


/* 가족무한사랑 유무선결합할인
   - Y0(0~14): 기본 가족결합 할인
   - Y1(15~29): 기본 + 11,000
   - Y2(30+):   기본 + 22,000
*/

INSERT INTO public.family_discount_policy
(policy_code, policy_name, min_user_count,
 min_monthly_amount_sum, max_monthly_amount_sum,
 min_joined_year_sum, max_joined_year_sum,
 total_discount_amount)
VALUES
-- =========================
-- Y0 : 합산 가입연수 0~14
-- =========================
('FDP-L2-Y0','가족무한사랑 유무선결합할인',2, 0, 68199, 0, 14, 1650),
('FDP-L3-Y0','가족무한사랑 유무선결합할인',3, 0, 68199, 0, 14, 2200),
('FDP-L4-Y0','가족무한사랑 유무선결합할인',4, 0, 68199, 0, 14, 2750),

('FDP-H2-Y0','가족무한사랑 유무선결합할인',2, 68200, NULL, 0, 14, 3300),
('FDP-H3-Y0','가족무한사랑 유무선결합할인',3, 68200, NULL, 0, 14, 4400),
('FDP-H4-Y0','가족무한사랑 유무선결합할인',4, 68200, NULL, 0, 14, 5500),

-- =========================
-- Y1 : 합산 가입연수 15~29 (기본 + 11,000)
-- =========================
('FDP-L2-Y1','가족무한사랑 유무선결합할인',2, 0, 68199, 15, 29, 12650), -- 1650 + 11000
('FDP-L3-Y1','가족무한사랑 유무선결합할인',3, 0, 68199, 15, 29, 13200), -- 2200 + 11000
('FDP-L4-Y1','가족무한사랑 유무선결합할인',4, 0, 68199, 15, 29, 13750), -- 2750 + 11000

('FDP-H2-Y1','가족무한사랑 유무선결합할인',2, 68200, NULL, 15, 29, 14300), -- 3300 + 11000
('FDP-H3-Y1','가족무한사랑 유무선결합할인',3, 68200, NULL, 15, 29, 15400), -- 4400 + 11000
('FDP-H4-Y1','가족무한사랑 유무선결합할인',4, 68200, NULL, 15, 29, 16500), -- 5500 + 11000

-- =========================
-- Y2 : 합산 가입연수 30+ (기본 + 22,000) / 상한 없음(NULL)
-- =========================
('FDP-L2-Y2','가족무한사랑 유무선결합할인',2, 0, 68199, 30, NULL, 23650), -- 1650 + 22000
('FDP-L3-Y2','가족무한사랑 유무선결합할인',3, 0, 68199, 30, NULL, 24200), -- 2200 + 22000
('FDP-L4-Y2','가족무한사랑 유무선결합할인',4, 0, 68199, 30, NULL, 24750), -- 2750 + 22000

('FDP-H2-Y2','가족무한사랑 유무선결합할인',2, 68200, NULL, 30, NULL, 25300), -- 3300 + 22000
('FDP-H3-Y2','가족무한사랑 유무선결합할인',3, 68200, NULL, 30, NULL, 26400), -- 4400 + 22000
('FDP-H4-Y2','가족무한사랑 유무선결합할인',4, 68200, NULL, 30, NULL, 27500)  -- 5500 + 22000
ON CONFLICT (policy_code) DO NOTHING;


/* 요금제 */
INSERT INTO public.plan
(plan_code, name, monthly_price, included_data_mb, excess_charge_per_mb, is_military_eligible, info, network_type_code, data_billing_type_code)
VALUES
-- =========================
-- 5G (NET-01) / 데이터 무제한 (DBT-01)
-- =========================
('PLN-001','5G 프리미어 에센셜',85000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-002','5G 스탠다드',75000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-003','5G 프리미어 레귤러',95000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-004','유쓰 5G 스탠다드',75000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-005','5G 데이터 레귤러',63000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-006','5G 데이터 플러스',66000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-007','5G 심플+',61000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-008','유쓰 5G 데이터 플러스',66000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-009','5G 라이트+',55000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-010','유쓰 5G 라이트+',55000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-011','5G 미니',37000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-012','5G 슬림+',47000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-013','5G 프리미어 플러스',105000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-014','5G 프리미어 슈퍼',115000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-015','5G 시니어 B형',43000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-016','유쓰 5G 슬림+',47000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-017','유쓰 5G 데이터 레귤러',63000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-018','5G 베이직+',59000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-019','5G 시니어 A형',45000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-020','유쓰 5G 미니',37000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-021','유쓰 5G 베이직+',59000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-022','5G 스탠다드 에센셜',70000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-023','유쓰 5G 심플+',61000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-024','5G 시니어 C형',39000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-025','5G 데이터 슈퍼',68000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-026','유쓰 5G 스탠다드 에센셜',70000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-027','5G 시그니처',130000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-028','유쓰 5G 데이터 슈퍼',68000,0,0.0000,true,'데이터 무제한','NET-01','DBT-01'),
('PLN-029','5G 라이트 청소년',45000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-030','5G 복지 55',55000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-031','Global 9GB',47000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-032','Global Unlimited',85000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-033','5G 복지 75',75000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-034','Global 5GB',37000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-035','Global 14GB',55000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-036','Global 80GB',66000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-037','Global 31GB',61000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-038','Global 150GB',75000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-039','5G 키즈 29',29000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-040','5G 키즈 39',39000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),
('PLN-041','5G 키즈 45',45000,0,0.0000,false,'데이터 무제한','NET-01','DBT-01'),

-- =========================
-- LTE (NET-02)
-- =========================
-- DBT-02: nMB 이후 과금(혹은 0MB부터 과금 포함) / 단가 0.2750
('PLN-042','(LTE) 데이터 시니어 33',33000,1700,0.2750,false,'데이터 1.7GB / 1MB당 0.275원 / 3GB 이상 무제한','NET-02','DBT-02'),
('PLN-043','(LTE) 데이터 33',33000,1500,0.2750,true,'데이터 1.5GB / 1MB당 0.275원 / 3GB 이상 무제한','NET-02','DBT-02'),
('PLN-044','LTE 표준',11990,0,0.2750,false,'데이터 1MB당 0.275원','NET-02','DBT-02'),
('PLN-045','시니어16.5',16500,300,0.7250,false,'데이터 300MB / 1MB당 0.725원 / 3GB 이상 무제한','NET-02','DBT-02'),
('PLN-046','LTE 선택형 요금제 250MB',20900,250,0.2750,false,'데이터 250MB / 1MB당 0.275원 / 3GB 이상 무제한','NET-02','DBT-02'),
('PLN-047','LTE 선택형 요금제 500MB',24750,500,0.2750,false,'데이터 500MB / 1MB당 0.275원 / 3GB 이상 무제한','NET-02','DBT-02'),
('PLN-048','LTE 선택형 요금제 1GB',28600,1000,0.2750,false,'데이터 1GB / 1MB당 0.275원 / 3GB 이상 무제한','NET-02','DBT-02'),

-- DBT-01: 완전 무제한
('PLN-049','(LTE) 추가 요금 걱정 없는 데이터 69',69000,0,0.0000,true,'데이터 무제한','NET-02','DBT-01'),
('PLN-050','(LTE) 현역병사 데이터 55',55000,0,0.0000,true,'데이터 무제한','NET-02','DBT-01'),
('PLN-051','(LTE) 현역병사 데이터 33',33000,0,0.0000,true,'데이터 무제한','NET-02','DBT-01'),
('PLN-052','(LTE) 추가 요금 걱정 없는 데이터 시니어 69',69000,0,0.0000,false,'데이터 무제한','NET-02','DBT-01'),
('PLN-053','(LTE) 추가 요금 걱정 없는 데이터 청소년 33',33000,0,0.0000,false,'데이터 무제한','NET-02','DBT-01'),
('PLN-054','(LTE) 추가 요금 걱정 없는 데이터 청소년 69',69000,0,0.0000,false,'데이터 무제한','NET-02','DBT-01'),
('PLN-055','LTE 키즈 22(만 12세 이하)',22000,0,0.0000,false,'데이터 무제한','NET-02','DBT-01'),
('PLN-056','(LTE) 추가 요금 걱정 없는 데이터 청소년 59',59000,0,0.0000,false,'데이터 무제한','NET-02','DBT-01'),
('PLN-057','(LTE) 복지 49',49000,0,0.0000,false,'데이터 무제한','NET-02','DBT-01'),

-- DBT-03: 초과 X(데이터 차단)
('PLN-058','LTE청소년19',20900,350,0.0000,false,'데이터 350MB / 데이터 차단','NET-02','DBT-03'),
('PLN-059','(LTE) 복지 33',33000,2000,0.0000,false,'데이터 2GB / 데이터 차단','NET-02','DBT-03')
ON CONFLICT (plan_code) DO NOTHING;


/* 요금제 기본 제공 부가 서비스 매핑 */
INSERT INTO plan_included_option (plan_code,option_service_code)
VALUES
    ('PLN-003','OPT-0012'),
    ('PLN-003','OPT-0014'),
    ('PLN-003','OPT-0015'),
    ('PLN-003','OPT-0016'),

    ('PLN-013','OPT-0003'),
    ('PLN-013','OPT-0013'),
    ('PLN-013','OPT-0002'),
    ('PLN-013','OPT-0006'),

    ('PLN-014','OPT-0003'),
    ('PLN-014','OPT-0013'),
    ('PLN-014','OPT-0002'),
    ('PLN-014','OPT-0006'),

    ('PLN-027','OPT-0003'),
    ('PLN-027','OPT-0013'),
    ('PLN-027','OPT-0002'),
    ('PLN-027','OPT-0006')
ON CONFLICT (plan_code, option_service_code) DO NOTHING;

/* 선택 약정 */
INSERT INTO contract_discount (duration_months,discount_percent)
VALUES (12,0.25),(24,0.25)
ON CONFLICT (duration_months) DO NOTHING;