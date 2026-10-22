# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2", "pymongo>=4.8"]
# ///
"""Job1 reliability 시나리오 한 개를 처음부터 끝까지 실행한다.

1. 필요하면 2026-01 정산 결과를 비운다 (S-001, S-003)
2. reliability.run에 실행을 남기고 0.25초마다 상태를 센다
3. worker를 띄워 calculateJob을 실행한다 (S-003은 장애 주입을 켠다)
4. 시나리오에 맞는 JUnit 검증을 돌리고 결과를 check_result, fault_cell에 남긴다

uv run infra/reliability/scripts/run_scenario.py S-001
"""

import argparse
import json
import os
import secrets
import subprocess
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

import psycopg
from pymongo import MongoClient

# 다른 worktree의 jar와 JUnit으로 돌리고 싶으면 REL_ROOT로 바꾼다
ROOT = Path(os.getenv("REL_ROOT", Path(__file__).resolve().parents[3]))
WORKER_JAR = ROOT / "apps/worker/build/libs/worker.jar"
RESULT_DIR = ROOT / "apps/worker/build/job1-reliability-results"
LOG_DIR = ROOT / "build/reliability-logs"

def find_java17():
    if os.getenv("REL_JAVA_HOME"):
        return os.environ["REL_JAVA_HOME"]
    try:
        # macOS
        return subprocess.run(["/usr/libexec/java_home", "-v", "17"], capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return os.environ["JAVA_HOME"]


JAVA_HOME = find_java17()
TARGET_PG = os.getenv("REL_TARGET_PG", "postgresql://postgres:postgres@localhost:25433/billing_target")
MONGO_URL = os.getenv("REL_MONGO", "mongodb://localhost:27027")
MONGO_DB = "tr1l"
CUTOFF = "2026-02-01T00:00:00Z"
BILLING_MONTH_DAY = "2026-01-01"
SAMPLE_INTERVAL = 0.25  # Step3가 몇 초 만에 끝나서 1초로는 변화가 거의 안 보인다

BASELINE_TEST = "com.tr1l.worker.reliability.Job1BaselineValidationTest"
PARTIAL_TEST = "com.tr1l.worker.reliability.Job1PartialSuccessValidationTest"

SCENARIOS = {
    "S-001": dict(name="normal-run", clean=True, fault=False,
                  test=f"{BASELINE_TEST}.normalRunFinalStateShouldSatisfyActiveInvariants"),
    "S-001R": dict(name="same-cutoff-rerun", clean=False, fault=False,
                   test=f"{BASELINE_TEST}.sameCutoffRerunShouldPreserveBaselineCounts"),
    "S-003": dict(name="after-mongo-before-status", clean=True, fault=True,
                  test=f"{PARTIAL_TEST}.afterMongoSaveFailureShouldLeaveTargetRowsWithSnapshots"),
    "S-003R": dict(name="rerun-after-partial-success", clean=False, fault=False,
                   test=f"{PARTIAL_TEST}.rerunAfterPartialSuccessShouldConvergeToS001RunState",
                   cell=("P3c", "FIRST", "K1")),
}

STATE_SQL = """
SELECT count(*) FILTER (WHERE status = 'TARGET'),
       count(*) FILTER (WHERE status = 'PROCESSING'),
       count(*) FILTER (WHERE status = 'PROCESSING' AND lease_until < now()),
       count(*) FILTER (WHERE status = 'CALCULATED'),
       count(*) FILTER (WHERE status = 'FAILED')
FROM billing_work
WHERE billing_month_day = %s
"""


def event(conn, run_id, event_type, detail=None):
    conn.execute(
        "INSERT INTO reliability.run_event (run_id, event_type, detail) VALUES (%s, %s, %s)",
        (run_id, event_type, detail),
    )
    print(f"[{datetime.now():%H:%M:%S}] {event_type} {detail or ''}", flush=True)


class Sampler(threading.Thread):
    """SAMPLE_INTERVAL마다 상태를 세고, 결과만 있는 work가 처음 보이면 장애 시점으로 남긴다."""

    def __init__(self, run_id, fault_armed):
        super().__init__(daemon=True)
        self.run_id = run_id
        self.fault_armed = fault_armed
        self.fault_seen = threading.Event()
        self.stopped = threading.Event()

    def run(self):
        snapshots = MongoClient(MONGO_URL)[MONGO_DB]["billing_snapshot"]
        with psycopg.connect(TARGET_PG, autocommit=True) as conn:
            while not self.stopped.is_set():
                started = time.monotonic()
                try:
                    target, processing, stale, calculated, failed = conn.execute(
                        STATE_SQL, (BILLING_MONTH_DAY,)
                    ).fetchone()
                except psycopg.errors.UndefinedTable:
                    target = processing = stale = calculated = failed = 0
                snapshot = snapshots.count_documents({"billingMonth": BILLING_MONTH_DAY})
                snapshot_only = max(snapshot - calculated, 0)
                conn.execute(
                    """INSERT INTO reliability.state_sample
                       (run_id, sampled_at, target_count, processing_count, stale_processing,
                        calculated_count, failed_count, snapshot_count, snapshot_only_count)
                       VALUES (%s, now(), %s, %s, %s, %s, %s, %s, %s) ON CONFLICT DO NOTHING""",
                    (self.run_id, target, processing, stale, calculated, failed, snapshot, snapshot_only),
                )
                if self.fault_armed and snapshot_only > 0 and not self.fault_seen.is_set():
                    self.fault_seen.set()
                    event(conn, self.run_id, "FAULT_INJECTED", "snapshot만 있는 work 첫 관측")
                self.stopped.wait(max(SAMPLE_INTERVAL - (time.monotonic() - started), 0))


def clean_month(conn):
    # billing_targets, billing_cycle은 worker가 처음 뜰 때 Liquibase가 만들어서 첫 실행에는 없다
    for table, column in (("billing_work", "billing_month_day"),
                          ("billing_targets", "billing_month"),
                          ("billing_cycle", "billing_month")):
        if conn.execute("SELECT to_regclass(%s)", (table,)).fetchone()[0]:
            conn.execute(f"DELETE FROM {table} WHERE {column} = DATE '2026-01-01'")
    MongoClient(MONGO_URL)[MONGO_DB]["billing_snapshot"].delete_many({"billingMonth": BILLING_MONTH_DAY})


def worker_env(fault):
    env = dict(os.environ)
    env.update(
        JAVA_HOME=JAVA_HOME,
        SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:25432/billing",
        SPRING_DATASOURCE_USERNAME="postgres",
        SPRING_DATASOURCE_PASSWORD="postgres",
        SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="30",
        PG_SUB_HOST="jdbc:postgresql://localhost:25433/billing_target",
        PG_SUB_USER="postgres",
        PG_SUB_PASSWORD="postgres",
        APP_DATASOURCE_TARGET_HIKARI_MAXIMUM_POOL_SIZE="30",
        SPRING_DATA_MONGODB_URI=f"{MONGO_URL}/{MONGO_DB}",
        MONGODB_URI=f"{MONGO_URL}/{MONGO_DB}",
        SPRING_BATCH_JDBC_INITIALIZE_SCHEMA="always",
        BATCH_ADD_RUN_ID="true",
        CHANNEL_ORDER="EMAIL,SMS",
        AWS_REGION="ap-northeast-2",
        # Job1은 암호화를 쓰지 않는다. 실행할 때마다 임의 키를 만든다
        SECRET_KEY=secrets.token_hex(16),
        TRANSFORMATION="AES/ECB/PKCS5Padding",
        APP_BILLING_RELIABILITY_FAIL_AFTER_SNAPSHOT_SAVE=str(fault).lower(),
    )
    return env


def run_worker(conn, run_id, fault, log_path):
    cmd = [
        f"{JAVA_HOME}/bin/java", "-Xmx2g", "-jar", str(WORKER_JAR),
        "--spring.batch.job.name=calculateJob",
        f"--batch.job1StartTime={CUTOFF}",
        "--server.port=18090",
    ]
    event(conn, run_id, "WORKER_START", "fault=on" if fault else "fault=off")
    with open(log_path, "w") as log:
        proc = subprocess.run(cmd, env=worker_env(fault), stdout=log, stderr=subprocess.STDOUT, cwd=ROOT)
    event(conn, run_id, "WORKER_EXIT", f"exit={proc.returncode}")
    return proc.returncode


def run_junit(test):
    env = dict(os.environ)
    env.update(
        JAVA_HOME=JAVA_HOME,
        JOB1_RELIABILITY_ENABLED="true",
        PG_SUB_HOST="jdbc:postgresql://localhost:25433/billing_target",
        PG_SUB_USER="postgres",
        PG_SUB_PASSWORD="postgres",
        MONGODB_URI=f"{MONGO_URL}/{MONGO_DB}",
    )
    cmd = ["./gradlew", ":apps:worker:test", "--tests", test, "--console=plain", "-q"]
    return subprocess.run(cmd, env=env, cwd=ROOT).returncode


def record_checks(conn, run_id, scenario_id, spec):
    directory = RESULT_DIR / f"{scenario_id}-{spec['name']}"
    validation = directory / "invariant-validation.json"
    if validation.exists():
        for result in json.loads(validation.read_text())["results"]:
            conn.execute(
                """INSERT INTO reliability.check_result (run_id, check_id, passed, violation_count, detail)
                   VALUES (%s, %s, %s, %s, %s) ON CONFLICT (run_id, check_id) DO UPDATE
                   SET passed = EXCLUDED.passed, violation_count = EXCLUDED.violation_count""",
                (run_id, result["invariantId"], result["passed"], result["violationCount"], result["title"]),
            )
    for name in ("s001-run-state-comparison.json", "baseline-comparison.json"):
        comparison = directory / name
        if comparison.exists():
            data = json.loads(comparison.read_text())
            conn.execute(
                """INSERT INTO reliability.check_result (run_id, check_id, passed, violation_count, detail)
                   VALUES (%s, 'S001_COMPARE', %s, %s, %s) ON CONFLICT (run_id, check_id) DO NOTHING""",
                (run_id, data["matched"], len(data["differences"]), json.dumps(data["currentMetrics"])),
            )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scenario", choices=SCENARIOS)
    args = parser.parse_args()
    scenario_id = args.scenario
    spec = SCENARIOS[scenario_id]

    LOG_DIR.mkdir(parents=True, exist_ok=True)
    run_id = f"{scenario_id}-{datetime.now():%Y%m%d-%H%M%S}"
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, cwd=ROOT).stdout.strip()
    cell = spec.get("cell") or (("P3c", "FIRST", "K1") if spec["fault"] else (None, None, None))

    with psycopg.connect(TARGET_PG, autocommit=True) as conn:
        conn.execute(
            """INSERT INTO reliability.run (run_id, scenario_id, dataset_id, fault_point, fault_timing, fault_kind, git_commit)
               VALUES (%s, %s, 'D1-rerun-mvp', %s, %s, %s, %s)""",
            (run_id, scenario_id, *cell, commit),
        )
        if spec["clean"]:
            clean_month(conn)
            event(conn, run_id, "CLEAN", "2026-01 정산 결과 비움")
        elif scenario_id.endswith("R"):
            event(conn, run_id, "RERUN_START", f"cutoff={CUTOFF}")

        sampler = Sampler(run_id, spec["fault"])
        sampler.start()
        time.sleep(2)
        exit_code = run_worker(conn, run_id, spec["fault"], LOG_DIR / f"{run_id}.log")
        time.sleep(3)
        sampler.stopped.set()
        sampler.join()

        event(conn, run_id, "VALIDATION", spec["test"].rsplit(".", 1)[1])
        junit_code = run_junit(spec["test"])
        record_checks(conn, run_id, scenario_id, spec)
        verdict = "PASS" if junit_code == 0 else "FAIL"
        conn.execute(
            "UPDATE reliability.run SET finished_at = now(), job_exit_code = %s, verdict = %s WHERE run_id = %s",
            (exit_code, verdict, run_id),
        )
        if spec.get("cell"):
            conn.execute(
                """UPDATE reliability.fault_cell SET status = %s, last_run_id = %s, updated_at = now()
                   WHERE fault_point = %s AND fault_timing = %s AND fault_kind = %s""",
                (verdict, run_id, *spec["cell"]),
            )
        print(f"{run_id} worker exit={exit_code} junit={verdict}")
        sys.exit(0 if verdict == "PASS" else 1)


if __name__ == "__main__":
    main()
