-- 정산 월 청구서 생성(formatJob) 진행 상태 (TR1L-181)
-- 운영 DB 에는 먼저 만들어 둔 테이블이 있어서 없을 때만 만든다
-- status: READY → RUNNING → FINISHED. RUNNING 은 lease_until 까지 그 실행이 가진다
CREATE TABLE IF NOT EXISTS format_cycle
(
    billing_month date        NOT NULL PRIMARY KEY,
    cutoff        timestamptz NOT NULL,
    status        varchar(10) NOT NULL,
    lease_until   timestamptz NULL
);
