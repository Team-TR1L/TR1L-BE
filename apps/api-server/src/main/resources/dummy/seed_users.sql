-- =====================================================
-- 유저 더미 데이터 100개 생성 (users: from_time/to_time 추가 반영)
--  - from_time, to_time: '02' 같은 2자리 시각 문자열
--  - from_time < to_time 항상 만족하도록 seq 기반으로 자동 생성
-- =====================================================

BEGIN;

-- (선택) 완전 초기화가 필요하면 아래를 주석 해제
-- TRUNCATE TABLE user_option_subscription;
-- TRUNCATE TABLE user_contract;
-- TRUNCATE TABLE user_soldier;
-- TRUNCATE TABLE monthly_data_usage;
-- TRUNCATE TABLE users;

-- =====================================================
-- TSID 기반 user_id 생성을 위한 함수
-- =====================================================
CREATE OR REPLACE FUNCTION generate_tsid(seq INT) RETURNS BIGINT AS $$
DECLARE
    base_ts      timestamptz := '2024-01-01 00:00:00+09'::timestamptz;
    timestamp_ms BIGINT;
    random_part  BIGINT;
BEGIN
    -- 2024-01-01(Asia/Seoul 기준) + seq 간격(100,000ms)
    timestamp_ms := (EXTRACT(EPOCH FROM base_ts) * 1000)::BIGINT + (seq::BIGINT * 100000);

    -- 하위 22bit 랜덤(0 ~ 2^22-1)
    random_part := FLOOR(random() * 4194304)::BIGINT;

    RETURN (timestamp_ms << 22) | random_part;
END;
$$ LANGUAGE plpgsql;


-- =====================================================
-- 유저 데이터 삽입 (100명)
--  - from_time/to_time 추가
--  - from_time < to_time 보장 (00~21 / 02~23)
--  - 이메일 unique 여부를 모르므로 "NOT EXISTS(email)" 방식 사용
-- =====================================================
WITH user_seed AS (
    SELECT * FROM (VALUES
        (1,  '김민수', 'minsu.kim@email.com', '010-1001-0001', DATE '1990-03-15', DATE '2020-01-10', 'PLN-001', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-01-10 10:00:00+09'::timestamptz, '2025-12-01 10:00:00+09'::timestamptz),
        (2,  '이서연', 'seoyeon.lee@email.com', '010-1001-0002', DATE '1992-07-22', DATE '2019-05-15', 'PLN-002', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-05-15 11:00:00+09'::timestamptz, '2025-12-01 11:00:00+09'::timestamptz),
        (3,  '박지훈', 'jihoon.park@email.com', '010-1001-0003', DATE '1988-11-30', DATE '2018-03-20', 'PLN-003', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-03-20 12:00:00+09'::timestamptz, '2025-12-01 12:00:00+09'::timestamptz),
        (4,  '최유진', 'yujin.choi@email.com', '010-1001-0004', DATE '1995-02-14', DATE '2021-08-05', 'PLN-005', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-08-05 13:00:00+09'::timestamptz, '2025-12-01 13:00:00+09'::timestamptz),
        (5,  '정하은', 'haeun.jung@email.com', '010-1001-0005', DATE '1993-09-18', DATE '2020-11-12', 'PLN-007', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-11-12 14:00:00+09'::timestamptz, '2025-12-01 14:00:00+09'::timestamptz),
        (6,  '강태양', 'taeyang.kang@email.com', '010-1001-0006', DATE '2002-04-25', DATE '2023-02-01', 'PLN-050', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-02-01 15:00:00+09'::timestamptz, '2025-12-01 15:00:00+09'::timestamptz),
        (7,  '윤성민', 'seongmin.yoon@email.com', '010-1001-0007', DATE '2003-06-10', DATE '2024-01-15', 'PLN-051', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2024-01-15 16:00:00+09'::timestamptz, '2025-12-01 16:00:00+09'::timestamptz),
        (8,  '임재현', 'jaehyun.lim@email.com', '010-1001-0008', DATE '2001-12-05', DATE '2022-09-10', 'PLN-002', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2022-09-10 17:00:00+09'::timestamptz, '2025-12-01 17:00:00+09'::timestamptz),

        (9,  '한영희', 'younghee.han@email.com', '010-1001-0009', DATE '1960-05-20', DATE '2015-07-01', 'PLN-015', 'WLF-02', TRUE,  'USER', 'ACTIVE', '2015-07-01 18:00:00+09'::timestamptz, '2025-12-01 18:00:00+09'::timestamptz),
        (10, '송기철', 'kichul.song@email.com', '010-1001-0010', DATE '1955-08-15', DATE '2010-03-10', 'PLN-030', 'WLF-03', TRUE,  'USER', 'ACTIVE', '2010-03-10 19:00:00+09'::timestamptz, '2025-12-01 19:00:00+09'::timestamptz),
        (11, '권순자', 'soonja.kwon@email.com', '010-1001-0011', DATE '1965-11-25', DATE '2012-06-20', 'PLN-033', 'WLF-04', TRUE,  'USER', 'ACTIVE', '2012-06-20 20:00:00+09'::timestamptz, '2025-12-01 20:00:00+09'::timestamptz),
        (12, '조만석', 'manseok.cho@email.com', '010-1001-0012', DATE '1958-03-30', DATE '2014-09-15', 'PLN-042', 'WLF-05', TRUE,  'USER', 'ACTIVE', '2014-09-15 21:00:00+09'::timestamptz, '2025-12-01 21:00:00+09'::timestamptz),

        (13, '서준호', 'junho.seo@email.com', '010-1001-0013', DATE '1985-01-12', DATE '2017-04-18', 'PLN-013', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-04-18 22:00:00+09'::timestamptz, '2025-12-01 22:00:00+09'::timestamptz),
        (14, '안지영', 'jiyoung.ahn@email.com', '010-1001-0014', DATE '1987-06-08', DATE '2016-11-22', 'PLN-014', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2016-11-22 23:00:00+09'::timestamptz, '2025-12-01 23:00:00+09'::timestamptz),
        (15, '홍성수', 'sungsu.hong@email.com', '010-1001-0015', DATE '1982-09-05', DATE '2015-02-28', 'PLN-027', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2015-02-28 10:30:00+09'::timestamptz, '2025-12-01 10:30:00+09'::timestamptz),
        (16, '김도윤', 'doyoon.kim@email.com', '010-1001-0016', DATE '2011-03-20', DATE '2023-05-10', 'PLN-029', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-05-10 11:30:00+09'::timestamptz, '2025-12-01 11:30:00+09'::timestamptz),
        (17, '이서아', 'seoa.lee@email.com', '010-1001-0017', DATE '2013-07-15', DATE '2024-02-20', 'PLN-039', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2024-02-20 12:30:00+09'::timestamptz, '2025-12-01 12:30:00+09'::timestamptz),
        (18, '박준서', 'junseo.park@email.com', '010-1001-0018', DATE '2012-11-08', DATE '2023-09-05', 'PLN-040', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-09-05 13:30:00+09'::timestamptz, '2025-12-01 13:30:00+09'::timestamptz),
        (19, '문수빈', 'soobin.moon@email.com', '010-1001-0019', DATE '1975-04-12', DATE '2019-07-18', 'PLN-049', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-07-18 14:30:00+09'::timestamptz, '2025-12-01 14:30:00+09'::timestamptz),
        (20, '장민지', 'minji.jang@email.com', '010-1001-0020', DATE '1998-08-22', DATE '2022-03-25', 'PLN-043', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2022-03-25 15:30:00+09'::timestamptz, '2025-12-01 15:30:00+09'::timestamptz),

        (21, '신동혁', 'donghyuk.shin@email.com', '010-1001-0021', DATE '1991-01-05', DATE '2020-02-14', 'PLN-006', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-02-14 16:00:00+09'::timestamptz, '2025-12-01 16:00:00+09'::timestamptz),
        (22, '유하늘', 'haneul.yu@email.com', '010-1001-0022', DATE '1994-05-18', DATE '2019-08-20', 'PLN-009', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-08-20 17:00:00+09'::timestamptz, '2025-12-01 17:00:00+09'::timestamptz),
        (23, '배소연', 'soyeon.bae@email.com', '010-1001-0023', DATE '1989-10-30', DATE '2018-12-05', 'PLN-011', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-12-05 18:00:00+09'::timestamptz, '2025-12-01 18:00:00+09'::timestamptz),
        (24, '오현우', 'hyunwoo.oh@email.com', '010-1001-0024', DATE '1996-03-22', DATE '2021-06-15', 'PLN-012', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-06-15 19:00:00+09'::timestamptz, '2025-12-01 19:00:00+09'::timestamptz),
        (25, '전수아', 'sua.jeon@email.com', '010-1001-0025', DATE '1993-07-08', DATE '2020-09-10', 'PLN-018', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-09-10 20:00:00+09'::timestamptz, '2025-12-01 20:00:00+09'::timestamptz),
        (26, '고민준', 'minjun.go@email.com', '010-1001-0026', DATE '2002-11-14', DATE '2023-03-22', 'PLN-020', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-03-22 21:00:00+09'::timestamptz, '2025-12-01 21:00:00+09'::timestamptz),
        (27, '남채원', 'chaewon.nam@email.com', '010-1001-0027', DATE '1995-02-28', DATE '2021-11-08', 'PLN-022', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-11-08 22:00:00+09'::timestamptz, '2025-12-01 22:00:00+09'::timestamptz),
        (28, '표지후', 'jihoo.pyo@email.com', '010-1001-0028', DATE '1990-06-19', DATE '2019-04-12', 'PLN-025', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-04-12 23:00:00+09'::timestamptz, '2025-12-01 23:00:00+09'::timestamptz),
        (29, '석예린', 'yerin.seok@email.com', '010-1001-0029', DATE '1992-09-03', DATE '2018-07-25', 'PLN-004', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-07-25 10:15:00+09'::timestamptz, '2025-12-01 10:15:00+09'::timestamptz),
        (30, '마서준', 'seojun.ma@email.com', '010-1001-0030', DATE '1997-12-16', DATE '2022-01-30', 'PLN-008', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2022-01-30 11:15:00+09'::timestamptz, '2025-12-01 11:15:00+09'::timestamptz),

        (31, '황현수', 'hyunsu.hwang@email.com', '010-1001-0031', DATE '2003-02-10', DATE '2024-05-12', 'PLN-051', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2024-05-12 12:15:00+09'::timestamptz, '2025-12-01 12:15:00+09'::timestamptz),
        (32, '노지안', 'jian.noh@email.com', '010-1001-0032', DATE '2002-08-25', DATE '2023-07-18', 'PLN-050', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-07-18 13:15:00+09'::timestamptz, '2025-12-01 13:15:00+09'::timestamptz),

        (33, '문영숙', 'youngsook.moon@email.com', '010-1001-0033', DATE '1962-04-08', DATE '2013-05-22', 'PLN-019', 'WLF-02', TRUE,  'USER', 'ACTIVE', '2013-05-22 14:15:00+09'::timestamptz, '2025-12-01 14:15:00+09'::timestamptz),
        (34, '차봉남', 'bongnam.cha@email.com', '010-1001-0034', DATE '1957-11-30', DATE '2011-08-15', 'PLN-024', 'WLF-03', TRUE,  'USER', 'ACTIVE', '2011-08-15 15:15:00+09'::timestamptz, '2025-12-01 15:15:00+09'::timestamptz),
        (35, '천금자', 'geumja.cheon@email.com', '010-1001-0035', DATE '1968-06-12', DATE '2014-02-28', 'PLN-057', 'WLF-04', TRUE,  'USER', 'ACTIVE', '2014-02-28 16:15:00+09'::timestamptz, '2025-12-01 16:15:00+09'::timestamptz),
        (36, '방순이', 'sooni.bang@email.com', '010-1001-0036', DATE '1959-09-22', DATE '2012-11-10', 'PLN-059', 'WLF-05', TRUE,  'USER', 'ACTIVE', '2012-11-10 17:15:00+09'::timestamptz, '2025-12-01 17:15:00+09'::timestamptz),

        (37, '길하진', 'hajin.gil@email.com', '010-1001-0037', DATE '1986-01-15', DATE '2017-03-08', 'PLN-001', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-03-08 18:00:00+09'::timestamptz, '2025-12-01 18:00:00+09'::timestamptz),
        (38, '우시우', 'siwoo.woo@email.com', '010-1001-0038', DATE '1994-07-20', DATE '2020-06-18', 'PLN-002', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-06-18 19:00:00+09'::timestamptz, '2025-12-01 19:00:00+09'::timestamptz),
        (39, '반유나', 'yuna.ban@email.com', '010-1001-0039', DATE '1991-11-05', DATE '2019-09-25', 'PLN-003', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-09-25 20:00:00+09'::timestamptz, '2025-12-01 20:00:00+09'::timestamptz),
        (40, '탁민서', 'minseo.tak@email.com', '010-1001-0040', DATE '1988-04-12', DATE '2018-01-30', 'PLN-005', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-01-30 21:00:00+09'::timestamptz, '2025-12-01 21:00:00+09'::timestamptz),
        (41, '진서율', 'seoyul.jin@email.com', '010-1001-0041', DATE '1995-08-28', DATE '2021-04-15', 'PLN-007', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-04-15 22:00:00+09'::timestamptz, '2025-12-01 22:00:00+09'::timestamptz),
        (42, '제갈민', 'min.jegal@email.com', '010-1001-0042', DATE '1992-02-14', DATE '2020-07-22', 'PLN-009', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-07-22 23:00:00+09'::timestamptz, '2025-12-01 23:00:00+09'::timestamptz),
        (43, '도하율', 'hayul.do@email.com', '010-1001-0043', DATE '1996-06-03', DATE '2022-02-10', 'PLN-011', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2022-02-10 10:20:00+09'::timestamptz, '2025-12-01 10:20:00+09'::timestamptz),
        (44, '변수현', 'soohyun.byun@email.com', '010-1001-0044', DATE '1993-10-18', DATE '2019-12-05', 'PLN-012', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-12-05 11:20:00+09'::timestamptz, '2025-12-01 11:20:00+09'::timestamptz),
        (45, '엄지원', 'jiwon.eom@email.com', '010-1001-0045', DATE '1989-03-25', DATE '2017-08-12', 'PLN-018', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-08-12 12:20:00+09'::timestamptz, '2025-12-01 12:20:00+09'::timestamptz),
        (46, '여은서', 'eunseo.yeo@email.com', '010-1001-0046', DATE '1997-07-09', DATE '2021-10-28', 'PLN-022', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-10-28 13:20:00+09'::timestamptz, '2025-12-01 13:20:00+09'::timestamptz),
        (47, '편시온', 'sion.pyun@email.com', '010-1001-0047', DATE '1994-11-22', DATE '2020-03-15', 'PLN-025', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-03-15 14:20:00+09'::timestamptz, '2025-12-01 14:20:00+09'::timestamptz),
        (48, '복지훈', 'jihoon.bok@email.com', '010-1001-0048', DATE '1990-05-07', DATE '2018-11-20', 'PLN-004', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-11-20 15:20:00+09'::timestamptz, '2025-12-01 15:20:00+09'::timestamptz),
        (49, '설아인', 'ain.seol@email.com', '010-1001-0049', DATE '1998-09-14', DATE '2022-06-08', 'PLN-008', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2022-06-08 16:20:00+09'::timestamptz, '2025-12-01 16:20:00+09'::timestamptz),
        (50, '빙서현', 'seohyun.bing@email.com', '010-1001-0050', DATE '1995-01-28', DATE '2021-01-12', 'PLN-010', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-01-12 17:20:00+09'::timestamptz, '2025-12-01 17:20:00+09'::timestamptz),

        (51, '허재원', 'jaewon.heo@email.com', '010-1001-0051', DATE '1987-04-16', DATE '2017-05-20', 'PLN-016', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-05-20 18:20:00+09'::timestamptz, '2025-12-01 18:20:00+09'::timestamptz),
        (52, '국하린', 'harin.kook@email.com', '010-1001-0052', DATE '1993-08-30', DATE '2019-10-15', 'PLN-017', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-10-15 19:20:00+09'::timestamptz, '2025-12-01 19:20:00+09'::timestamptz),
        (53, '갈예준', 'yejoon.gal@email.com', '010-1001-0053', DATE '1991-12-11', DATE '2018-04-22', 'PLN-021', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-04-22 20:20:00+09'::timestamptz, '2025-12-01 20:20:00+09'::timestamptz),
        (54, '양서아', 'seoa.yang@email.com', '010-1001-0054', DATE '1996-03-05', DATE '2021-07-08', 'PLN-023', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-07-08 21:20:00+09'::timestamptz, '2025-12-01 21:20:00+09'::timestamptz),
        (55, '봉시후', 'sihoo.bong@email.com', '010-1001-0055', DATE '1994-07-19', DATE '2020-12-14', 'PLN-026', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-12-14 22:20:00+09'::timestamptz, '2025-12-01 22:20:00+09'::timestamptz),
        (56, '사도현', 'dohyun.sa@email.com', '010-1001-0056', DATE '1988-11-23', DATE '2017-09-30', 'PLN-028', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-09-30 23:20:00+09'::timestamptz, '2025-12-01 23:20:00+09'::timestamptz),
        (57, '동민재', 'minjae.dong@email.com', '010-1001-0057', DATE '1992-02-06', DATE '2019-03-18', 'PLN-031', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-03-18 10:40:00+09'::timestamptz, '2025-12-01 10:40:00+09'::timestamptz),
        (58, '옥도윤', 'doyoon.ok@email.com', '010-1001-0058', DATE '1995-06-21', DATE '2021-08-25', 'PLN-032', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-08-25 11:40:00+09'::timestamptz, '2025-12-01 11:40:00+09'::timestamptz),
        (59, '선지우', 'jiwoo.sun@email.com', '010-1001-0059', DATE '1990-10-15', DATE '2018-02-12', 'PLN-034', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-02-12 12:40:00+09'::timestamptz, '2025-12-01 12:40:00+09'::timestamptz),
        (60, '뇌수호', 'suho.noe@email.com', '010-1001-0060', DATE '1997-01-29', DATE '2022-05-05', 'PLN-035', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2022-05-05 13:40:00+09'::timestamptz, '2025-12-01 13:40:00+09'::timestamptz),

        (61, '지서진', 'seojin.ji@email.com', '010-1001-0061', DATE '1993-05-13', DATE '2020-11-19', 'PLN-036', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-11-19 14:40:00+09'::timestamptz, '2025-12-01 14:40:00+09'::timestamptz),
        (62, '뢰우진', 'woojin.roe@email.com', '010-1001-0062', DATE '1989-09-27', DATE '2017-06-28', 'PLN-037', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-06-28 15:40:00+09'::timestamptz, '2025-12-01 15:40:00+09'::timestamptz),
        (63, '추서윤', 'seoyoon.chu@email.com', '010-1001-0063', DATE '1996-01-10', DATE '2021-03-14', 'PLN-038', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-03-14 16:40:00+09'::timestamptz, '2025-12-01 16:40:00+09'::timestamptz),
        (64, '견지안', 'jian.gyun@email.com', '010-1001-0064', DATE '2012-04-22', DATE '2023-08-30', 'PLN-041', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-08-30 17:40:00+09'::timestamptz, '2025-12-01 17:40:00+09'::timestamptz),
        (65, '상하준', 'hajun.sang@email.com', '010-1001-0065', DATE '2013-08-05', DATE '2024-01-22', 'PLN-055', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2024-01-22 18:40:00+09'::timestamptz, '2025-12-01 18:40:00+09'::timestamptz),
        (66, '두예서', 'yeseo.doo@email.com', '010-1001-0066', DATE '1975-11-18', DATE '2016-09-10', 'PLN-042', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2016-09-10 19:40:00+09'::timestamptz, '2025-12-01 19:40:00+09'::timestamptz),
        (67, '피시현', 'sihyun.pi@email.com', '010-1001-0067', DATE '1998-03-02', DATE '2022-07-16', 'PLN-043', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2022-07-16 20:40:00+09'::timestamptz, '2025-12-01 20:40:00+09'::timestamptz),
        (68, '제민호', 'minho.je@email.com', '010-1001-0068', DATE '1991-07-14', DATE '2019-01-28', 'PLN-044', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-01-28 21:40:00+09'::timestamptz, '2025-12-01 21:40:00+09'::timestamptz),
        (69, '애서연', 'seoyeon.ae@email.com', '010-1001-0069', DATE '1964-10-26', DATE '2015-04-15', 'PLN-045', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2015-04-15 22:40:00+09'::timestamptz, '2025-12-01 22:40:00+09'::timestamptz),
        (70, '간준혁', 'joonhyuk.gan@email.com', '010-1001-0070', DATE '1987-02-08', DATE '2017-11-05', 'PLN-046', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-11-05 23:40:00+09'::timestamptz, '2025-12-01 23:40:00+09'::timestamptz),

        (71, '소윤서', 'yoonseo.so@email.com', '010-1001-0071', DATE '1992-05-20', DATE '2019-06-12', 'PLN-047', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-06-12 09:00:00+09'::timestamptz, '2025-12-01 09:00:00+09'::timestamptz),
        (72, '팽민서', 'minseo.paeng@email.com', '010-1001-0072', DATE '1995-09-03', DATE '2021-09-28', 'PLN-048', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-09-28 09:30:00+09'::timestamptz, '2025-12-01 09:30:00+09'::timestamptz),
        (73, '공지훈', 'jihoon.gong@email.com', '010-1001-0073', DATE '1988-12-16', DATE '2018-05-14', 'PLN-049', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-05-14 10:00:00+09'::timestamptz, '2025-12-01 10:00:00+09'::timestamptz),
        (74, '학서준', 'seojun.hak@email.com', '010-1001-0074', DATE '2003-03-28', DATE '2024-02-08', 'PLN-050', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2024-02-08 10:30:00+09'::timestamptz, '2025-12-01 10:30:00+09'::timestamptz),
        (75, '용민준', 'minjoon.yong@email.com', '010-1001-0075', DATE '2002-07-11', DATE '2023-04-19', 'PLN-051', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-04-19 11:00:00+09'::timestamptz, '2025-12-01 11:00:00+09'::timestamptz),
        (76, '사공유준', 'yujoon.sagong@email.com', '010-1001-0076', DATE '1991-10-24', DATE '2019-08-06', 'PLN-052', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-08-06 11:30:00+09'::timestamptz, '2025-12-01 11:30:00+09'::timestamptz),
        (77, '선우하윤', 'hayoon.sunwoo@email.com', '010-1001-0077', DATE '2010-01-05', DATE '2023-06-22', 'PLN-053', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-06-22 12:00:00+09'::timestamptz, '2025-12-01 12:00:00+09'::timestamptz),
        (78, '남궁서아', 'seoa.namgung@email.com', '010-1001-0078', DATE '2011-04-18', DATE '2024-03-10', 'PLN-054', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2024-03-10 12:30:00+09'::timestamptz, '2025-12-01 12:30:00+09'::timestamptz),
        (79, '독고시우', 'siwoo.dokgo@email.com', '010-1001-0079', DATE '2013-07-30', DATE '2024-09-15', 'PLN-055', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2024-09-15 13:00:00+09'::timestamptz, '2025-12-01 13:00:00+09'::timestamptz),
        (80, '황보민재', 'minjae.hwangbo@email.com', '010-1001-0080', DATE '2011-11-12', DATE '2023-11-28', 'PLN-056', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-11-28 13:30:00+09'::timestamptz, '2025-12-01 13:30:00+09'::timestamptz),

        (81, '강영자', 'youngja.kang@email.com', '010-1001-0081', DATE '1961-02-14', DATE '2014-07-20', 'PLN-030', 'WLF-02', TRUE,  'USER', 'ACTIVE', '2014-07-20 14:00:00+09'::timestamptz, '2025-12-01 14:00:00+09'::timestamptz),
        (82, '임철수', 'chulsoo.lim@email.com', '010-1001-0082', DATE '1956-05-27', DATE '2010-10-05', 'PLN-033', 'WLF-03', TRUE,  'USER', 'ACTIVE', '2010-10-05 14:30:00+09'::timestamptz, '2025-12-01 14:30:00+09'::timestamptz),
        (83, '윤점순', 'jeomsoon.yoon@email.com', '010-1001-0083', DATE '1963-08-09', DATE '2013-01-18', 'PLN-015', 'WLF-04', TRUE,  'USER', 'ACTIVE', '2013-01-18 15:00:00+09'::timestamptz, '2025-12-01 15:00:00+09'::timestamptz),
        (84, '박갑동', 'gapdong.park@email.com', '010-1001-0084', DATE '1958-11-21', DATE '2011-04-25', 'PLN-019', 'WLF-05', TRUE,  'USER', 'ACTIVE', '2011-04-25 15:30:00+09'::timestamptz, '2025-12-01 15:30:00+09'::timestamptz),
        (85, '조길순', 'gilsoon.cho@email.com', '010-1001-0085', DATE '1966-03-04', DATE '2015-08-12', 'PLN-024', 'WLF-02', TRUE,  'USER', 'ACTIVE', '2015-08-12 16:00:00+09'::timestamptz, '2025-12-01 16:00:00+09'::timestamptz),
        (86, '이춘자', 'chunja.lee@email.com', '010-1001-0086', DATE '1959-06-16', DATE '2012-12-30', 'PLN-042', 'WLF-03', TRUE,  'USER', 'ACTIVE', '2012-12-30 16:30:00+09'::timestamptz, '2025-12-01 16:30:00+09'::timestamptz),
        (87, '김말순', 'malsoon.kim@email.com', '010-1001-0087', DATE '1965-09-28', DATE '2014-03-22', 'PLN-057', 'WLF-04', TRUE,  'USER', 'ACTIVE', '2014-03-22 17:00:00+09'::timestamptz, '2025-12-01 17:00:00+09'::timestamptz),
        (88, '신복남', 'boknam.shin@email.com', '010-1001-0088', DATE '1960-12-10', DATE '2013-07-08', 'PLN-059', 'WLF-05', TRUE,  'USER', 'ACTIVE', '2013-07-08 17:30:00+09'::timestamptz, '2025-12-01 17:30:00+09'::timestamptz),
        (89, '홍순덕', 'soonduk.hong@email.com', '010-1001-0089', DATE '1962-04-22', DATE '2014-11-15', 'PLN-052', 'WLF-02', TRUE,  'USER', 'ACTIVE', '2014-11-15 18:00:00+09'::timestamptz, '2025-12-01 18:00:00+09'::timestamptz),
        (90, '최명자', 'myungja.choi@email.com', '010-1001-0090', DATE '1957-07-05', DATE '2011-02-28', 'PLN-015', 'WLF-03', TRUE,  'USER', 'ACTIVE', '2011-02-28 18:30:00+09'::timestamptz, '2025-12-01 18:30:00+09'::timestamptz),

        (91, '백도현', 'dohyun.baek@email.com', '010-1001-0091', DATE '1990-01-17', DATE '2018-06-14', 'PLN-001', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2018-06-14 19:00:00+09'::timestamptz, '2025-12-01 19:00:00+09'::timestamptz),
        (92, '임유진', 'yujin.im@email.com', '010-1001-0092', DATE '1994-05-29', DATE '2020-10-22', 'PLN-013', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-10-22 19:30:00+09'::timestamptz, '2025-12-01 19:30:00+09'::timestamptz),
        (93, '송재희', 'jaehee.song@email.com', '010-1001-0093', DATE '1987-09-11', DATE '2017-01-05', 'PLN-014', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-01-05 20:00:00+09'::timestamptz, '2025-12-01 20:00:00+09'::timestamptz),
        (94, '한정우', 'jungwoo.han@email.com', '010-1001-0094', DATE '1992-12-24', DATE '2019-05-18', 'PLN-027', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2019-05-18 20:30:00+09'::timestamptz, '2025-12-01 20:30:00+09'::timestamptz),
        (95, '권시연', 'siyeon.kwon@email.com', '010-1001-0095', DATE '1996-04-06', DATE '2021-12-03', 'PLN-005', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-12-03 21:00:00+09'::timestamptz, '2025-12-01 21:00:00+09'::timestamptz),
        (96, '유도윤', 'doyoon.yoo@email.com', '010-1001-0096', DATE '2003-08-19', DATE '2024-04-12', 'PLN-050', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2024-04-12 21:30:00+09'::timestamptz, '2025-12-01 21:30:00+09'::timestamptz),
        (97, '서민석', 'minseok.seo@email.com', '010-1001-0097', DATE '2002-11-01', DATE '2023-09-25', 'PLN-051', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2023-09-25 22:00:00+09'::timestamptz, '2025-12-01 22:00:00+09'::timestamptz),
        (98, '정하람', 'haram.jung@email.com', '010-1001-0098', DATE '1993-02-13', DATE '2020-01-08', 'PLN-007', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2020-01-08 22:30:00+09'::timestamptz, '2025-12-01 22:30:00+09'::timestamptz),
        (99, '최예은', 'yeeun.choi@email.com', '010-1001-0099', DATE '1989-06-25', DATE '2017-12-20', 'PLN-011', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2017-12-20 23:00:00+09'::timestamptz, '2025-12-01 23:00:00+09'::timestamptz),
        (100,'박서윤', 'seoyoon.park@email.com','010-1001-0100', DATE '1995-10-07', DATE '2021-05-15', 'PLN-018', 'WLF-01', FALSE, 'USER', 'ACTIVE', '2021-05-15 23:30:00+09'::timestamptz, '2025-12-01 23:30:00+09'::timestamptz)
    ) AS t(seq, name, email, phone_number, birth_date, join_date, plan_code, welfare_code, is_welfare, user_role, user_status, created_at, modified_at)
)
INSERT INTO users (
    user_id, name, email, phone_number, birth_date, join_date,
    plan_code, welfare_code, is_welfare,
    user_role, user_status,
    from_time, to_time,day_time,
    created_at, modified_at
)
SELECT
    generate_tsid(seq),
    name, email, phone_number, birth_date, join_date,
    plan_code, welfare_code, is_welfare,
    user_role, user_status,

    -- from_time/to_time: 항상 2자리, 항상 from < to (00~21 / 02~23)
    lpad(((seq - 1) % 22)::text, 2, '0')                  AS from_time,
    lpad((((seq - 1) % 22) + 2)::text, 2, '0')            AS to_time,
    lpad((1 + floor(random() * 28))::int::text, 2, '0')   AS day_time,

    created_at, modified_at
FROM user_seed s
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.email = s.email
);


-- =====================================================
-- 군인 정보 (user_soldier)
-- =====================================================
WITH soldier_seed(email, start_date, end_date) AS (
    VALUES
        ('taeyang.kang@email.com', DATE '2023-02-15', DATE '2025-11-15'),
        ('seongmin.yoon@email.com', DATE '2024-02-01', DATE '2026-01-01'),
        ('jaehyun.lim@email.com',   DATE '2022-10-01', DATE '2024-09-01'),
        ('hyunsu.hwang@email.com',  DATE '2024-06-01', DATE '2026-05-01'),
        ('jian.noh@email.com',      DATE '2023-08-01', DATE '2025-07-01'),
        ('seojun.hak@email.com',    DATE '2024-03-01', DATE '2026-02-01'),
        ('minjoon.yong@email.com',  DATE '2023-05-01', DATE '2025-04-01'),
        ('doyoon.yoo@email.com',    DATE '2024-05-01', DATE '2026-04-01'),
        ('minseok.seo@email.com',   DATE '2023-10-01', DATE '2025-09-01')
)
INSERT INTO user_soldier (user_id, start_date, end_date)
SELECT u.user_id, s.start_date, s.end_date
FROM soldier_seed s
JOIN users u ON u.email = s.email
WHERE NOT EXISTS (
    SELECT 1 FROM user_soldier us WHERE us.user_id = u.user_id
);


-- =====================================================
-- 선택 약정 (user_contract) - 40명
-- =====================================================
WITH contract_seed(email, start_date, end_date) AS (
    VALUES
        ('minsu.kim@email.com',       DATE '2024-01-01', DATE '2026-01-01'),
        ('seoyeon.lee@email.com',     DATE '2023-06-01', DATE '2025-06-01'),
        ('jihoon.park@email.com',     DATE '2024-03-01', DATE '2025-03-01'),
        ('yujin.choi@email.com',      DATE '2024-07-01', DATE '2026-07-01'),
        ('haeun.jung@email.com',      DATE '2023-09-01', DATE '2025-09-01'),
        ('junho.seo@email.com',       DATE '2024-05-01', DATE '2026-05-01'),
        ('jiyoung.ahn@email.com',     DATE '2023-12-01', DATE '2025-12-01'),
        ('sungsu.hong@email.com',     DATE '2024-02-01', DATE '2026-02-01'),
        ('donghyuk.shin@email.com',   DATE '2024-08-01', DATE '2026-08-01'),
        ('haneul.yu@email.com',       DATE '2023-11-01', DATE '2025-11-01'),
        ('soyeon.bae@email.com',      DATE '2024-04-01', DATE '2026-04-01'),
        ('hyunwoo.oh@email.com',      DATE '2024-06-01', DATE '2025-06-01'),
        ('sua.jeon@email.com',        DATE '2023-10-01', DATE '2025-10-01'),
        ('seojun.ma@email.com',       DATE '2024-01-15', DATE '2026-01-15'),
        ('hajin.gil@email.com',       DATE '2024-09-01', DATE '2026-09-01'),
        ('siwoo.woo@email.com',       DATE '2023-07-01', DATE '2025-07-01'),
        ('yuna.ban@email.com',        DATE '2024-10-01', DATE '2026-10-01'),
        ('minseo.tak@email.com',      DATE '2023-08-01', DATE '2025-08-01'),
        ('seoyul.jin@email.com',      DATE '2024-11-01', DATE '2026-11-01'),
        ('min.jegal@email.com',       DATE '2024-12-01', DATE '2026-12-01'),
        ('hayul.do@email.com',        DATE '2024-02-15', DATE '2026-02-15'),
        ('soohyun.byun@email.com',    DATE '2023-05-01', DATE '2025-05-01'),
        ('jiwon.eom@email.com',       DATE '2024-03-20', DATE '2026-03-20'),
        ('eunseo.yeo@email.com',      DATE '2023-12-15', DATE '2025-12-15'),
        ('sion.pyun@email.com',       DATE '2024-07-20', DATE '2026-07-20'),
        ('jihoon.bok@email.com',      DATE '2024-04-10', DATE '2026-04-10'),
        ('ain.seol@email.com',        DATE '2023-06-20', DATE '2025-06-20'),
        ('seohyun.bing@email.com',    DATE '2024-08-25', DATE '2026-08-25'),
        ('jaewon.heo@email.com',      DATE '2024-05-15', DATE '2026-05-15'),
        ('harin.kook@email.com',      DATE '2023-09-10', DATE '2025-09-10'),
        ('yejoon.gal@email.com',      DATE '2024-10-20', DATE '2026-10-20'),
        ('seoa.yang@email.com',       DATE '2024-01-25', DATE '2026-01-25'),
        ('sihoo.bong@email.com',      DATE '2023-11-15', DATE '2025-11-15'),
        ('dohyun.sa@email.com',       DATE '2024-06-10', DATE '2026-06-10'),
        ('minjae.dong@email.com',     DATE '2024-09-15', DATE '2026-09-15'),
        ('doyoon.ok@email.com',       DATE '2023-10-25', DATE '2025-10-25'),
        ('jiwoo.sun@email.com',       DATE '2024-11-20', DATE '2026-11-20'),
        ('suho.noe@email.com',        DATE '2024-12-10', DATE '2026-12-10'),
        ('seojin.ji@email.com',       DATE '2024-02-28', DATE '2026-02-28'),
        ('woojin.roe@email.com',      DATE '2023-07-15', DATE '2025-07-15')
)
INSERT INTO user_contract (user_id, start_date, end_date)
SELECT u.user_id, c.start_date, c.end_date
FROM contract_seed c
JOIN users u ON u.email = c.email
WHERE NOT EXISTS (
    SELECT 1
    FROM user_contract uc
    WHERE uc.user_id = u.user_id
      AND uc.start_date = c.start_date
      AND uc.end_date = c.end_date
);


-- =====================================================
-- 부가 서비스 구독 (user_option_subscription) - 30명
-- =====================================================
WITH opt_seed(email, option_service_code, start_at, end_at) AS (
    VALUES
        ('minsu.kim@email.com',     'OPT-0001', '2024-01-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('seoyeon.lee@email.com',  'OPT-0002', '2024-03-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('yujin.choi@email.com',   'OPT-0005', '2023-06-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('haeun.jung@email.com',   'OPT-0006', '2024-05-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('donghyuk.shin@email.com','OPT-0007', '2024-02-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('haneul.yu@email.com',    'OPT-0008', '2023-09-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('soyeon.bae@email.com',   'OPT-0009', '2024-07-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('hyunwoo.oh@email.com',   'OPT-0010', '2024-04-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('sua.jeon@email.com',     'OPT-0011', '2023-11-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('seojun.ma@email.com',    'OPT-0001', '2024-08-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('hajin.gil@email.com',    'OPT-0002', '2024-06-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('siwoo.woo@email.com',    'OPT-0005', '2023-12-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('yuna.ban@email.com',     'OPT-0006', '2024-09-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('minseo.tak@email.com',   'OPT-0007', '2024-10-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('seoyul.jin@email.com',   'OPT-0008', '2023-08-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('min.jegal@email.com',    'OPT-0009', '2024-11-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('hayul.do@email.com',     'OPT-0010', '2024-12-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('soohyun.byun@email.com', 'OPT-0011', '2023-07-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('jiwon.eom@email.com',    'OPT-0001', '2024-01-15 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('eunseo.yeo@email.com',   'OPT-0002', '2024-02-20 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('sion.pyun@email.com',    'OPT-0005', '2023-10-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('jihoon.bok@email.com',   'OPT-0006', '2024-03-15 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('ain.seol@email.com',     'OPT-0007', '2024-04-20 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('seohyun.bing@email.com', 'OPT-0008', '2023-05-01 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('jaewon.heo@email.com',   'OPT-0009', '2024-05-25 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('harin.kook@email.com',   'OPT-0010', '2024-06-30 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('yejoon.gal@email.com',   'OPT-0011', '2023-09-15 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('seoa.yang@email.com',    'OPT-0001', '2024-07-10 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('sihoo.bong@email.com',   'OPT-0002', '2024-08-15 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz),
        ('dohyun.sa@email.com',    'OPT-0005', '2023-11-20 00:00:00+09'::timestamptz, '2025-12-31 23:59:59+09'::timestamptz)
)
INSERT INTO user_option_subscription (user_id, option_service_code, start_date, end_date)
SELECT u.user_id, o.option_service_code, o.start_at, o.end_at
FROM opt_seed o
JOIN users u ON u.email = o.email
WHERE NOT EXISTS (
    SELECT 1
    FROM user_option_subscription s
    WHERE s.user_id = u.user_id
      AND s.option_service_code = o.option_service_code
      AND s.start_date = o.start_at
      AND s.end_date = o.end_at
);


-- =====================================================
-- 2025년 12월 사용량 (usage_year_month = 'YYYYMM')
-- =====================================================
INSERT INTO monthly_data_usage (user_id, usage_year_month, used_data_mb, usage_aggregated_at)
SELECT
    u.user_id,
    '202512'::varchar(6) AS usage_year_month,
    CASE
        WHEN p.data_billing_type_code = 'DBT-01' THEN (50000 + (random() * 150000))::BIGINT
        WHEN p.data_billing_type_code = 'DBT-02' THEN (COALESCE(p.included_data_mb, 0) + (random() * 2000))::BIGINT
        ELSE (COALESCE(NULLIF(p.included_data_mb, 0), 5000) * (0.5 + random() * 0.5))::BIGINT
    END AS used_data_mb,
    '2025-12-31 23:59:59+09'::timestamptz AS usage_aggregated_at
FROM users u
JOIN plan p ON u.plan_code = p.plan_code
WHERE NOT EXISTS (
    SELECT 1
    FROM monthly_data_usage m
    WHERE m.user_id = u.user_id
      AND m.usage_year_month = '202512'
);

COMMIT;
