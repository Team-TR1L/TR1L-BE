-- Job1 reliability 결과 수집 스키마
-- 운영 테이블과 섞이지 않게 target DB의 reliability 스키마에만 둔다
-- 여러 번 실행해도 안전하다

CREATE SCHEMA IF NOT EXISTS reliability;

-- 실행 한 번
CREATE TABLE IF NOT EXISTS reliability.run
(
    run_id        varchar(64)  PRIMARY KEY,
    scenario_id   varchar(32)  NOT NULL,
    dataset_id    varchar(64)  NOT NULL,
    seed          bigint       NULL,
    fault_point   varchar(16)  NULL,     -- P0 ~ P4, 장애 없으면 NULL
    fault_timing  varchar(16)  NULL,     -- FIRST, MIDDLE, LAST
    fault_kind    varchar(16)  NULL,     -- K1 ~ K5
    git_commit    varchar(40)  NULL,
    started_at    timestamptz  NOT NULL DEFAULT now(),
    finished_at   timestamptz  NULL,
    job_exit_code integer      NULL,
    verdict       varchar(16)  NULL,     -- PASS, FAIL
    allure_url    text         NULL
);

-- 재실행 안전 장치를 깨뜨린 mutant 로 돌린 실행이면 그 ID (M1 ~ M6). 실패가 기대값이라 실패율 집계에서 뺀다
ALTER TABLE reliability.run ADD COLUMN IF NOT EXISTS mutant varchar(8) NULL;
-- 장애 → 재실행 중 장애 → 재실행처럼 이어서 돈 실행을 한 묶음으로 본다
ALTER TABLE reliability.run ADD COLUMN IF NOT EXISTS chain_id varchar(64) NULL;

-- 장애 주입, worker 시작과 종료 같은 사건. Grafana annotation으로 그린다
CREATE TABLE IF NOT EXISTS reliability.run_event
(
    id          bigserial    PRIMARY KEY,
    run_id      varchar(64)  NOT NULL REFERENCES reliability.run (run_id),
    occurred_at timestamptz  NOT NULL DEFAULT now(),
    event_type  varchar(32)  NOT NULL,   -- WORKER_START, FAULT_INJECTED, WORKER_EXIT, RERUN_START, VALIDATION
    detail      text         NULL
);

-- 1초마다 센 상태. sampler가 쓴다
CREATE TABLE IF NOT EXISTS reliability.state_sample
(
    run_id              varchar(64) NOT NULL REFERENCES reliability.run (run_id),
    sampled_at          timestamptz NOT NULL DEFAULT now(),
    target_count        bigint      NOT NULL,
    processing_count    bigint      NOT NULL,
    stale_processing    bigint      NOT NULL,
    calculated_count    bigint      NOT NULL,
    failed_count        bigint      NOT NULL,
    snapshot_count      bigint      NOT NULL,
    snapshot_only_count bigint      NOT NULL, -- snapshot은 있는데 CALCULATED가 아닌 work 수 (snapshot_count - calculated_count)
    PRIMARY KEY (run_id, sampled_at)
);

-- 실행마다 조건(C1~C6)과 invariant 결과
CREATE TABLE IF NOT EXISTS reliability.check_result
(
    run_id          varchar(64) NOT NULL REFERENCES reliability.run (run_id),
    check_id        varchar(32) NOT NULL,   -- C1 ~ C6, INV-001 ...
    passed          boolean     NOT NULL,
    violation_count bigint      NOT NULL DEFAULT 0,
    detail          text        NULL,
    checked_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, check_id)
);

-- 장애 행렬 한 칸의 최신 상태
CREATE TABLE IF NOT EXISTS reliability.fault_cell
(
    fault_point  varchar(16) NOT NULL,
    fault_timing varchar(16) NOT NULL,
    fault_kind   varchar(16) NOT NULL,
    status       varchar(16) NOT NULL,    -- PASS, FAIL, NOT_RUN, EXCLUDED
    last_run_id  varchar(64) NULL,
    note         text        NULL,
    updated_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (fault_point, fault_timing, fault_kind)
);

-- 장애 행렬 칸 정의. 처음에는 모두 NOT_RUN, 이유가 있는 칸만 EXCLUDED
INSERT INTO reliability.fault_cell (fault_point, fault_timing, fault_kind, status, note)
SELECT p.point, t.timing, k.kind,
       CASE
           WHEN p.point IN ('P0', 'P4') AND t.timing <> 'FIRST' THEN 'EXCLUDED'
           WHEN k.kind = 'K5' AND p.point NOT IN ('P3a') THEN 'EXCLUDED'
           ELSE 'NOT_RUN'
       END,
       CASE
           WHEN p.point IN ('P0', 'P4') AND t.timing <> 'FIRST' THEN 'chunk가 없는 tasklet 단계'
           WHEN k.kind = 'K5' AND p.point NOT IN ('P3a') THEN 'worker 경합은 Step3 claim 구간에서만 의미가 있음'
       END
FROM (VALUES ('P0'), ('P1'), ('P2'), ('P3a'), ('P3b'), ('P3c'), ('P3d'), ('P4')) AS p(point)
CROSS JOIN (VALUES ('FIRST'), ('MIDDLE'), ('LAST')) AS t(timing)
CROSS JOIN (VALUES ('K1'), ('K2'), ('K3'), ('K4'), ('K5')) AS k(kind)
ON CONFLICT DO NOTHING;

-- Step4 는 MongoDB 를 쓰지 않아 MongoDB 연결을 끊어도 장애가 나지 않는다
UPDATE reliability.fault_cell
SET status = 'EXCLUDED', note = 'Step4 는 MongoDB 를 쓰지 않음'
WHERE fault_point = 'P4' AND fault_kind = 'K4' AND status = 'NOT_RUN';
