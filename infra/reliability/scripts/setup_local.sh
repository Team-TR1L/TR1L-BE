#!/usr/bin/env bash
# Job1 reliability 로컬 DB 초기화
#   setup_local.sh [데이터셋 ID] [seed]
#   예) setup_local.sh                  D1-rerun-mvp
#       setup_local.sh D3-pairwise
#       setup_local.sh D4-random 20261022
#   main DB   스키마 → 마스터 데이터 → 데이터셋 시드
#   target DB billing_work → reliability 스키마
# billing_targets, billing_cycle은 worker가 뜰 때 Liquibase(dev context)가 만든다
# main DB는 schema_dev.sql이 테이블을 지우고 다시 만들기 때문에 여러 번 실행해도 된다
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
MAIN="postgresql://postgres:postgres@localhost:25432/billing"
TARGET="postgresql://postgres:postgres@localhost:25433/billing_target"
DATASET="${1:-D1-rerun-mvp}"
SEED="${2:-}"
DATASET_DIR="$ROOT/apps/worker/src/test/resources/reliability/datasets/$DATASET"

if [[ ! -d "$DATASET_DIR/main-db" ]]; then
  echo "데이터셋이 없다: $DATASET_DIR" >&2
  exit 1
fi

echo "[main] schema_dev.sql"
# CREATE INDEX CONCURRENTLY가 있어 트랜잭션으로 묶지 않는다
psql "$MAIN" -q -v ON_ERROR_STOP=1 -f "$ROOT/apps/api-server/src/main/resources/db/sql/schema_dev.sql"

echo "[main] data_dev.sql"
psql "$MAIN" -q -v ON_ERROR_STOP=1 -f "$ROOT/apps/api-server/src/main/resources/db/sql/data_dev.sql"

echo "[main] $DATASET seed${SEED:+ (seed=$SEED)}"
for seed_file in "$DATASET_DIR"/main-db/*.sql; do
  psql "$MAIN" -q -v ON_ERROR_STOP=1 ${SEED:+-v seed="$SEED"} -f "$seed_file"
done

echo "[target] billing_work"
psql "$TARGET" -q -v ON_ERROR_STOP=1 -f "$ROOT/apps/worker/src/test/resources/reliability/ai/context/job1/02_billing_work_ddl.sql"

echo "[target] reliability schema"
psql "$TARGET" -q -v ON_ERROR_STOP=1 -f "$ROOT/infra/reliability/sql/01_reliability_schema.sql"

psql "$MAIN" -At -c "SELECT 'users=' || count(*) FROM users"
psql "$MAIN" -At -c "SELECT 'option_subscriptions=' || count(*) FROM user_option_subscription"
psql "$TARGET" -At -c "SELECT 'fault_cells=' || count(*) FROM reliability.fault_cell"

# 지금 적재된 데이터셋을 기록해 둔다. run_scenario.py 와 검증 JUnit 이 읽는다
mkdir -p "$ROOT/build"
printf '%s\n%s\n' "$DATASET" "$SEED" > "$ROOT/build/reliability-dataset"

# 적재한 분포가 dataset.yml 과 같은지 바로 확인한다. 다르면 여기서 실패한다
echo "[check] $DATASET distribution vs dataset.yml"
(cd "$ROOT" && JOB1_RELIABILITY_ENABLED=true JOB1_RELIABILITY_DATASET="$DATASET" \
  PG_MAIN_HOST="jdbc:postgresql://localhost:25432/billing" \
  ./gradlew :apps:worker:test --rerun --tests 'com.tr1l.worker.reliability.Job1DatasetDistributionValidationTest' --console=plain -q)
echo "  결과: apps/worker/build/job1-reliability-results/dataset/$DATASET-distribution.json"
