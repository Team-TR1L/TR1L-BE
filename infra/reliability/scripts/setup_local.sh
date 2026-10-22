#!/usr/bin/env bash
# Job1 reliability 로컬 DB 초기화
#   main DB   스키마 → 마스터 데이터 → D1 시드
#   target DB billing_work → reliability 스키마
# billing_targets, billing_cycle은 worker가 뜰 때 Liquibase(dev context)가 만든다
# main DB는 schema_dev.sql이 테이블을 지우고 다시 만들기 때문에 여러 번 실행해도 된다
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
MAIN="postgresql://postgres:postgres@localhost:25432/billing"
TARGET="postgresql://postgres:postgres@localhost:25433/billing_target"
D1="$ROOT/apps/worker/src/test/resources/reliability/datasets/D1-rerun-mvp"

echo "[main] schema_dev.sql"
# CREATE INDEX CONCURRENTLY가 있어 트랜잭션으로 묶지 않는다
psql "$MAIN" -q -v ON_ERROR_STOP=1 -f "$ROOT/apps/api-server/src/main/resources/db/sql/schema_dev.sql"

echo "[main] data_dev.sql"
psql "$MAIN" -q -v ON_ERROR_STOP=1 -f "$ROOT/apps/api-server/src/main/resources/db/sql/data_dev.sql"

echo "[main] D1 seed"
psql "$MAIN" -q -v ON_ERROR_STOP=1 -f "$D1/main-db/01_seed_d1.sql"

echo "[target] billing_work"
psql "$TARGET" -q -v ON_ERROR_STOP=1 -f "$ROOT/apps/worker/src/test/resources/reliability/ai/context/job1/02_billing_work_ddl.sql"

echo "[target] reliability schema"
psql "$TARGET" -q -v ON_ERROR_STOP=1 -f "$ROOT/infra/reliability/sql/01_reliability_schema.sql"

psql "$MAIN" -At -c "SELECT 'users=' || count(*) FROM users"
psql "$MAIN" -At -c "SELECT 'option_subscriptions=' || count(*) FROM user_option_subscription"
psql "$TARGET" -At -c "SELECT 'fault_cells=' || count(*) FROM reliability.fault_cell"
