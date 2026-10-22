# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2", "pymongo>=4.8"]
# ///
"""Job1 reliability 시나리오 한 개를 처음부터 끝까지 실행한다.

1. 필요하면 2026-01 정산 결과를 비운다 (S-001, S-003)
2. reliability.run에 실행을 남기고 0.25초마다 상태를 센다
3. worker를 띄워 calculateJob을 실행한다 (S-003은 장애 주입을 켠다)
4. 시나리오에 맞는 JUnit 검증을 돌리고 결과를 check_result, fault_cell에 남긴다

S-002 → S-002E → S-002R 은 claim 직후 강제 종료, lease 만료 전 재실행, 만료 뒤 재실행 순서로 이어서 돈다.
S-011 은 worker 두 개를 같은 cutoff 로 같이 돌린다 (장애 행렬 K5).

uv run infra/reliability/scripts/run_scenario.py S-001
"""

import argparse
import json
import os
import secrets
import subprocess
import sys
import threading
import urllib.request
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
BOUNDARY_TEST = "com.tr1l.worker.reliability.Job1BoundaryCaseValidationTest"
FAULT_TEST = "com.tr1l.worker.reliability.Job1FaultMatrixValidationTest"
LEASE_TEST = "com.tr1l.worker.reliability.Job1LeaseContentionValidationTest"
# chunk 가 없는 tasklet 지점은 시점을 FIRST 하나로만 본다
TASKLET_POINTS = {"P0", "P4"}

TOXIPROXY = os.getenv("REL_TOXIPROXY", "http://localhost:18474")
PROXY_PORTS = {"target-pg": 25443, "mongo": 27037}
# 장애 종류별 worker 설정과 끊을 proxy
#   K1 예외, K2 프로세스 즉시 종료, K3 target DB 연결 끊김, K4 MongoDB 연결 끊김
FAULT_KINDS = {
    "K1": dict(kind="EXCEPTION", proxy=None),
    "K2": dict(kind="HALT", proxy=None),
    "K3": dict(kind="PAUSE", proxy="target-pg"),
    "K4": dict(kind="PAUSE", proxy="mongo"),
    # K5 는 worker A 를 P3a 에서 멈춘 사이 worker B 를 띄운다 (S-011)
    "K5": dict(kind="PAUSE", proxy=None),
}
# K2 ~ K4 는 반환 listener 가 돌지 못해 PROCESSING 이 lease 끝날 때까지 남는다
# 장애 실행에는 lease 를 짧게 주고, 재실행은 lease 가 끝난 뒤에 한다
SHORT_LEASE_SECONDS = 20
# S-002 는 lease 가 끝나기 전에 S-002E 를 한 번 더 돌려야 해서 넉넉히 준다
S002_LEASE_SECONDS = 180
# K5 는 두 worker 가 Step3 에서 claim 을 나눠 가진다. lease 는 짧게 주되 chunk 하나 처리 시간보다는 길게 준다
K5_LEASE_SECONDS = 20
# B 를 다시 진행시키는 시점. A 의 Step3 가 이만큼 CALCULATED 를 만든 뒤에 B 를 재개한다
K5_RELEASE_RATIO = {"FIRST": 0.0, "MIDDLE": 0.5, "LAST": 0.9}
# 두 worker 가 같은 순간에 JobExecution 을 만들면 batch 메타 테이블의 SERIALIZABLE 트랜잭션끼리 부딪혀
# 한쪽이 시작도 못 하고 끝난다. B 는 조금 늦게 띄운다
K5_SECOND_DELAY_SECONDS = 3
WORKER_PORT, SECOND_WORKER_PORT = 18090, 18091

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
    # 장애 행렬 한 칸. --point, --timing 으로 고른다
    "S-010": dict(name="fault-matrix-cell", clean=True, fault=False, matrix=True,
                  test=f"{FAULT_TEST}.afterFaultShouldMatchExpectedStateOfPoint"),
    "S-010R": dict(name="rerun-after-fault-matrix-cell", clean=False, fault=False,
                   test=f"{FAULT_TEST}.rerunAfterFaultShouldConvergeToS001RunState"),
    # P3a 에서 강제 종료해 PROCESSING 을 lease 안쪽으로 남긴다. 기본 시점은 LAST (FIRST, MIDDLE 은 commit 전이라 안 남는다)
    "S-002": dict(name="claim-halt-processing-left", clean=True, fault=False, lease_fault=True,
                  test=f"{LEASE_TEST}.claimHaltShouldLeaveProcessingWithinLease"),
    # lease 가 끝나기 전에 다시 돌린다
    "S-002E": dict(name="rerun-before-lease-expiry", clean=False, fault=False, lease_followup=True,
                   test=f"{LEASE_TEST}.rerunBeforeLeaseExpiryShouldNotTakeLeasedWork"),
    # lease 가 끝난 뒤 다시 돌린다
    "S-002R": dict(name="rerun-after-lease-expiry", clean=False, fault=False, lease_followup=True, wait_lease=True,
                   test=f"{LEASE_TEST}.rerunAfterLeaseExpiryShouldReclaimOnceAndConverge"),
    # 장애 행렬 K5 한 칸. worker 두 개를 같은 cutoff 로 같이 돌린다
    "S-011": dict(name="concurrent-workers", clean=True, fault=False, concurrent=True,
                  test=f"{LEASE_TEST}.concurrentWorkersShouldCalculateEachWorkOnce"),
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


def worker_env(fault, extra=None):
    env = dict(os.environ)
    env.update(
        JAVA_HOME=JAVA_HOME,
        SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:25432/billing",
        SPRING_DATASOURCE_USERNAME="postgres",
        SPRING_DATASOURCE_PASSWORD="postgres",
        SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="30",
        # worker 는 Toxiproxy 를 거쳐 target DB, MongoDB 에 붙는다 (K3, K4 에서 끊는다)
        PG_SUB_HOST=f"jdbc:postgresql://localhost:{PROXY_PORTS['target-pg']}/billing_target",
        PG_SUB_USER="postgres",
        PG_SUB_PASSWORD="postgres",
        APP_DATASOURCE_TARGET_HIKARI_MAXIMUM_POOL_SIZE="30",
        SPRING_DATA_MONGODB_URI=f"mongodb://localhost:{PROXY_PORTS['mongo']}/{MONGO_DB}",
        MONGODB_URI=f"mongodb://localhost:{PROXY_PORTS['mongo']}/{MONGO_DB}",
        SPRING_BATCH_JDBC_INITIALIZE_SCHEMA="always",
        BATCH_ADD_RUN_ID="true",
        CHANNEL_ORDER="EMAIL,SMS",
        AWS_REGION="ap-northeast-2",
        # Job1은 암호화를 쓰지 않는다. 실행할 때마다 임의 키를 만든다
        SECRET_KEY=secrets.token_hex(16),
        TRANSFORMATION="AES/ECB/PKCS5Padding",
        APP_BILLING_RELIABILITY_FAIL_AFTER_SNAPSHOT_SAVE=str(fault).lower(),
    )
    env.update(extra or {})
    return env


def worker_cmd(port=WORKER_PORT):
    return [
        f"{JAVA_HOME}/bin/java", "-Xmx2g", "-jar", str(WORKER_JAR),
        "--spring.batch.job.name=calculateJob",
        f"--batch.job1StartTime={CUTOFF}",
        f"--server.port={port}",
    ]


def run_worker(conn, run_id, fault, log_path, extra_env=None, label=None):
    event(conn, run_id, "WORKER_START", label or ("fault=on" if fault else "fault=off"))
    with open(log_path, "w") as log:
        proc = subprocess.run(worker_cmd(), env=worker_env(fault, extra_env), stdout=log, stderr=subprocess.STDOUT, cwd=ROOT)
    event(conn, run_id, "WORKER_EXIT", f"exit={proc.returncode}")
    return proc.returncode


def loaded_dataset():
    """setup_local.sh 가 남긴 지금 적재된 데이터셋과 seed"""
    marker = ROOT / "build/reliability-dataset"
    if not marker.exists():
        return "D1-rerun-mvp", None
    lines = marker.read_text().splitlines() + ["", ""]
    return lines[0] or "D1-rerun-mvp", (int(lines[1]) if lines[1].strip() else None)


def run_junit(tests, dataset_id, extra_env=None):
    env = dict(os.environ)
    env.update(
        JAVA_HOME=JAVA_HOME,
        JOB1_RELIABILITY_ENABLED="true",
        JOB1_RELIABILITY_DATASET=dataset_id,
        PG_SUB_HOST="jdbc:postgresql://localhost:25433/billing_target",
        PG_SUB_USER="postgres",
        PG_SUB_PASSWORD="postgres",
        PG_MAIN_HOST="jdbc:postgresql://localhost:25432/billing",
        MONGODB_URI=f"{MONGO_URL}/{MONGO_DB}",
    )
    env.update(extra_env or {})
    # 검증 JUnit은 DB 상태만 읽어서 Gradle 입력이 같으면 UP-TO-DATE로 건너뛴다. --rerun으로 항상 실행한다
    cmd = ["./gradlew", ":apps:worker:test", "--rerun", "--console=plain", "-q"]
    for test in tests:
        cmd += ["--tests", test]
    return subprocess.run(cmd, env=env, cwd=ROOT).returncode


def set_proxy(name, enabled):
    body = json.dumps({"enabled": enabled}).encode()
    request = urllib.request.Request(f"{TOXIPROXY}/proxies/{name}", data=body, method="POST",
                                     headers={"Content-Type": "application/json"})
    urllib.request.urlopen(request, timeout=5).read()


def enable_all_proxies():
    for name in PROXY_PORTS:
        set_proxy(name, True)


class PauseWatcher(threading.Thread):
    """worker 가 PAUSE 지점에서 멈추면 proxy 를 끊고 다시 진행시킨다."""

    def __init__(self, pause_dir, proxy, run_id, label):
        super().__init__(daemon=True)
        self.pause_dir, self.proxy, self.run_id, self.label = pause_dir, proxy, run_id, label
        self.triggered = threading.Event()
        self.stopped = threading.Event()

    def run(self):
        paused = self.pause_dir / "paused"
        while not self.stopped.is_set():
            if paused.exists():
                set_proxy(self.proxy, False)
                (self.pause_dir / "resume").write_text("")
                self.triggered.set()
                with psycopg.connect(TARGET_PG, autocommit=True) as conn:
                    event(conn, self.run_id, "FAULT_INJECTED", f"{self.label} proxy={self.proxy} 끊음")
                return
            self.stopped.wait(0.05)


class SecondWorker(threading.Thread):
    """K5. 같은 cutoff 로 worker B 를 띄우고, A 와 B 를 Step2 마지막 쓰기 직후(P2)에 붙잡아 둔다.
    A 를 먼저 재개하고, A 의 Step3 가 K5_RELEASE_RATIO 만큼 진행되면 B 를 재개한다.

    Step2 끝에서 붙잡는 이유
    - 준비 실행으로 billing_work 가 이미 있어서 Step2 는 아무 행도 넣지 않는다. 멈춰 있어도 잠그는 행이 없다
    - 한쪽이 Step3 에서 claim 을 쥐고 있으면 다른 쪽 Step2 INSERT ... ON CONFLICT DO NOTHING 이 그 행에서 기다린다.
      그래서 그냥 같이 띄우면 늦은 worker 는 Step3 에 왔을 때 할 일이 거의 없다"""

    def __init__(self, run_id, timing, pause_a, pause_b, log_path, extra_env):
        super().__init__(daemon=True)
        self.run_id, self.timing = run_id, timing
        self.pause_a, self.pause_b = pause_a, pause_b
        self.log_path, self.extra_env = log_path, extra_env
        self.proc = None
        self.released = threading.Event()

    def run(self):
        time.sleep(K5_SECOND_DELAY_SECONDS)
        with psycopg.connect(TARGET_PG, autocommit=True) as conn:
            event(conn, self.run_id, "WORKER_START", "worker B")
            with open(self.log_path, "w") as log:
                self.proc = subprocess.Popen(worker_cmd(SECOND_WORKER_PORT), env=worker_env(False, self.extra_env),
                                             stdout=log, stderr=subprocess.STDOUT, cwd=ROOT)
                try:
                    self.coordinate(conn)
                finally:
                    # 어느 쪽도 멈춘 채 남지 않게 항상 재개 파일을 남긴다
                    for directory in (self.pause_a, self.pause_b):
                        directory.mkdir(parents=True, exist_ok=True)
                        (directory / "resume").write_text("")
                self.proc.wait()
            event(conn, self.run_id, "WORKER_EXIT", f"worker B exit={self.proc.returncode}")

    def coordinate(self, conn):
        deadline = time.monotonic() + 90
        while not ((self.pause_a / "paused").exists() and (self.pause_b / "paused").exists()):
            if self.proc.poll() is not None or time.monotonic() > deadline:
                return
            time.sleep(0.05)
        event(conn, self.run_id, "WORKER_GATE", "A, B 모두 Step2 끝에서 멈춤")
        (self.pause_a / "resume").write_text("")
        total = conn.execute("SELECT count(*) FROM billing_work WHERE billing_month_day = %s",
                             (BILLING_MONTH_DAY,)).fetchone()[0]
        goal = total * K5_RELEASE_RATIO[self.timing]
        while conn.execute("""SELECT count(*) FROM billing_work
                              WHERE billing_month_day = %s AND status = 'CALCULATED'""",
                           (BILLING_MONTH_DAY,)).fetchone()[0] < goal:
            time.sleep(0.02)
        calculated = conn.execute("""SELECT count(*) FROM billing_work
                                     WHERE billing_month_day = %s AND status = 'CALCULATED'""",
                                  (BILLING_MONTH_DAY,)).fetchone()[0]
        (self.pause_b / "resume").write_text("")
        self.released.set()
        event(conn, self.run_id, "FAULT_INJECTED", f"worker B 재개, A 진행 {calculated} / {total}")


def prepare_step3(conn, run_id):
    """K5 준비. Step0 ~ Step2 까지만 끝내 두고 work 를 모두 TARGET 으로 남긴다.

    두 worker 를 처음부터 같이 띄우면 Step2 INSERT ... ON CONFLICT DO NOTHING 이 먼저 Step3 에 들어간
    worker 의 claim 행에서 기다려서, 늦은 worker 는 Step3 에 왔을 때 할 일이 거의 없다.
    Step3 의 모든 claim 에서 예외를 던지면 claim 이 롤백되어 Step0 ~ Step2 결과만 남는다."""
    event(conn, run_id, "PREPARE", "Step0 ~ Step2 만 실행")
    log_path = LOG_DIR / f"{run_id}-prepare.log"
    with open(log_path, "w") as log:
        subprocess.run(worker_cmd(), env=worker_env(False, dict(
            APP_RELIABILITY_FAULT_POINT="P3a", APP_RELIABILITY_FAULT_NTH="1",
            APP_RELIABILITY_FAULT_REPEAT="FROM_NTH", APP_RELIABILITY_FAULT_KIND="EXCEPTION")),
            stdout=log, stderr=subprocess.STDOUT, cwd=ROOT)
    target, others = conn.execute(
        """SELECT count(*) FILTER (WHERE status = 'TARGET' AND attempt_count = 0), count(*) FILTER (WHERE status <> 'TARGET')
           FROM billing_work WHERE billing_month_day = %s""", (BILLING_MONTH_DAY,)).fetchone()
    snapshots = MongoClient(MONGO_URL)[MONGO_DB]["billing_snapshot"].count_documents({"billingMonth": BILLING_MONTH_DAY})
    event(conn, run_id, "PREPARE", f"TARGET {target}, 그 밖의 상태 {others}, snapshot {snapshots}")
    return target > 0 and others == 0 and snapshots == 0


def lease_remaining(conn):
    """남은 PROCESSING 중 가장 먼저 끝나는 lease 까지 남은 초. PROCESSING 이 없으면 None"""
    return conn.execute(
        """SELECT extract(epoch FROM min(lease_until) - now()) FROM billing_work
           WHERE billing_month_day = %s AND status = 'PROCESSING'""", (BILLING_MONTH_DAY,)).fetchone()[0]


def wait_lease_expiry(conn, run_id):
    """남은 PROCESSING 의 lease 가 끝날 때까지 기다린다."""
    lease_until = conn.execute(
        """SELECT max(lease_until) FROM billing_work
           WHERE billing_month_day = %s AND status = 'PROCESSING'""", (BILLING_MONTH_DAY,)).fetchone()[0]
    if lease_until is None:
        return
    remaining = (lease_until - datetime.now(lease_until.tzinfo)).total_seconds() + 2
    if remaining > 0:
        event(conn, run_id, "LEASE_WAIT", f"{remaining:.0f}초")
        time.sleep(remaining)


def hits_path(dataset_id):
    """S-001 이 남긴 지점별 통과 횟수. 시점을 몇 번째 호출로 바꿀 때 쓴다"""
    return ROOT / f"build/reliability-fault-hits-{dataset_id}.json"


def nth_for(point, timing, dataset_id):
    if point in TASKLET_POINTS:
        return 1
    path = hits_path(dataset_id)
    if not path.exists():
        sys.exit(f"{path} 가 없다. 같은 데이터셋으로 S-001 을 먼저 돌린다")
    total = json.loads(path.read_text())[point]
    # 여러 스레드로 도는 Step 은 실행마다 호출 수가 조금씩 달라 마지막 호출을 정확히 맞출 수 없다
    # LAST 는 마지막 10% 구간이 시작되는 호출로 잡는다
    return {"FIRST": 1, "MIDDLE": max(1, (total + 1) // 2), "LAST": max(1, -(-total * 9 // 10))}[timing]


def insert_check(conn, run_id, check_id, passed, violation_count, detail):
    conn.execute(
        """INSERT INTO reliability.check_result (run_id, check_id, passed, violation_count, detail)
           VALUES (%s, %s, %s, %s, %s) ON CONFLICT (run_id, check_id) DO UPDATE
           SET passed = EXCLUDED.passed, violation_count = EXCLUDED.violation_count, detail = EXCLUDED.detail""",
        (run_id, check_id, passed, violation_count, detail),
    )


def record_checks(conn, run_id, scenario_id, spec, since):
    directory = RESULT_DIR / f"{scenario_id}-{spec['name']}"

    def fresh(name):
        # 이번 실행에서 JUnit이 쓴 파일만 읽는다. 이전 실행 결과가 섞이지 않게 한다
        path = directory / name
        return json.loads(path.read_text()) if path.exists() and path.stat().st_mtime >= since else None

    if validation := fresh("invariant-validation.json"):
        for result in validation["results"]:
            insert_check(conn, run_id, result["invariantId"], result["passed"], result["violationCount"], result["title"])
    if comparison := fresh("s001-run-state-comparison.json"):
        insert_check(conn, run_id, "S001_COMPARE", comparison["matched"], len(comparison["differences"]),
                     json.dumps(comparison["currentMetrics"]))
    if hashes := fresh("snapshot-hash-comparison.json"):
        violations = hashes["hashMismatch"] + hashes["missingInCurrent"] + hashes["extraInCurrent"] + hashes["duplicateUsers"]
        insert_check(conn, run_id, "C5", violations == 0, violations,
                     f"snapshot 해시 일치 {hashes['hashMatch']} / 기준 {hashes['referenceCount']}")
    boundary_path = RESULT_DIR / "dataset" / "D2-boundary-boundary-cases.json"
    if boundary_path.exists() and boundary_path.stat().st_mtime >= since:
        cases = json.loads(boundary_path.read_text())["cases"]
        failed = [case["code"] for case in cases if not case["passed"]]
        insert_check(conn, run_id, "BOUNDARY", not failed, len(failed),
                     f"경계값 {len(cases) - len(failed)} / {len(cases)} 일치" + (f", 다름 {failed[:5]}" if failed else ""))
    if lease := fresh("lease-check.json"):
        insert_check(conn, run_id, lease["checkId"], lease["passed"], lease["violationCount"], lease["detail"])
    if amounts := fresh("reference-amount-comparison.json"):
        violations = amounts["mismatchedUsers"] + amounts["missingSnapshots"] + amounts["unexpectedSnapshots"]
        insert_check(conn, run_id, "C6", violations == 0, violations,
                     f"참조 금액 일치 {amounts['matchedUsers']} / 대상 {amounts['expectedUsers']}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scenario", choices=SCENARIOS)
    parser.add_argument("--point", help="S-010 장애 지점 (P0 ~ P4)")
    parser.add_argument("--timing", choices=["FIRST", "MIDDLE", "LAST"],
                        help="기본은 FIRST, S-002 는 LAST")
    parser.add_argument("--kind", default="K1", choices=list(FAULT_KINDS))
    args = parser.parse_args()
    scenario_id = args.scenario
    spec = SCENARIOS[scenario_id]

    LOG_DIR.mkdir(parents=True, exist_ok=True)
    run_id = f"{scenario_id}-{datetime.now():%Y%m%d-%H%M%S}"
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, cwd=ROOT).stdout.strip()
    cell = spec.get("cell") or (("P3c", "FIRST", "K1") if spec["fault"] else (None, None, None))
    dataset_id, seed = loaded_dataset()
    worker_extra, junit_extra, label, matrix_cell, watcher, second = {}, {}, None, None, None, None
    precondition = None  # 시나리오 전제가 깨졌으면 그 이유. 그 실행은 검증한 것이 아니다
    pause_dir = LOG_DIR / f"{run_id}-pause"
    hits_file = LOG_DIR / f"{run_id}-hits.json"
    worker_extra["APP_RELIABILITY_FAULT_HITS_FILE"] = str(hits_file)

    if spec.get("matrix"):
        if not args.point:
            sys.exit("S-010 은 --point 가 필요하다")
        timing = "FIRST" if args.point in TASKLET_POINTS else (args.timing or "FIRST")
        nth = nth_for(args.point, timing, dataset_id)
        fault_kind = FAULT_KINDS[args.kind]
        cell = (args.point, timing, args.kind)
        worker_extra.update(APP_RELIABILITY_FAULT_POINT=args.point, APP_RELIABILITY_FAULT_NTH=str(nth),
                            APP_RELIABILITY_FAULT_REPEAT="ONCE", APP_RELIABILITY_FAULT_KIND=fault_kind["kind"],
                            APP_RELIABILITY_FAULT_PAUSE_DIR=str(pause_dir))
        if args.kind != "K1":
            worker_extra["BATCH_JOB1_STEP3_LEASE_SECONDS"] = str(SHORT_LEASE_SECONDS)
        junit_extra.update(JOB1_FAULT_POINT=args.point, JOB1_FAULT_TIMING=timing, JOB1_FAULT_KIND=args.kind)
        label = f"fault={args.point} {timing} {args.kind} #{nth}"
    elif spec.get("lease_fault"):
        # 첫 partition 이 두 번째 claim 을 하는 순간에 죽인다. 다른 partition 은 첫 chunk 를 커밋하고
        # 둘째 chunk 를 처리하는 중이라, 커밋된 PROCESSING 이 남는다. 첫 claim 들(FIRST, MIDDLE)에서 죽이면
        # 아직 커밋된 claim 이 없어 남는 것이 없고, LAST 는 실행마다 남기도 하고 안 남기도 한다
        timing = "MIDDLE"
        nth = nth_for("P3a", "MIDDLE", dataset_id) + 1
        cell = ("P3a", timing, "K2")
        worker_extra.update(APP_RELIABILITY_FAULT_POINT="P3a", APP_RELIABILITY_FAULT_NTH=str(nth),
                            APP_RELIABILITY_FAULT_REPEAT="ONCE", APP_RELIABILITY_FAULT_KIND="HALT",
                            BATCH_JOB1_STEP3_LEASE_SECONDS=str(S002_LEASE_SECONDS))
        label = f"fault=P3a {timing} K2 #{nth} lease={S002_LEASE_SECONDS}s"
    elif spec.get("lease_followup"):
        # 바로 앞 S-002 를 이어받는다
        with psycopg.connect(TARGET_PG, autocommit=True) as conn:
            row = conn.execute(
                """SELECT fault_point, fault_timing, fault_kind FROM reliability.run
                   WHERE scenario_id = 'S-002' ORDER BY started_at DESC LIMIT 1""").fetchone()
        if not row:
            sys.exit("S-002 를 먼저 돌린다")
        cell = row
    elif spec.get("concurrent"):
        timing = args.timing or "FIRST"
        cell = ("P3a", timing, "K5")
        pause_b = LOG_DIR / f"{run_id}-pause-b"
        gate = dict(BATCH_JOB1_STEP3_LEASE_SECONDS=str(K5_LEASE_SECONDS), APP_RELIABILITY_FAULT_POINT="P2",
                    APP_RELIABILITY_FAULT_NTH="1", APP_RELIABILITY_FAULT_REPEAT="FROM_NTH",
                    APP_RELIABILITY_FAULT_KIND="PAUSE")
        worker_extra.update(gate, APP_RELIABILITY_FAULT_PAUSE_DIR=str(pause_dir))
        junit_extra.update(JOB1_FAULT_POINT="P3a", JOB1_FAULT_TIMING=timing, JOB1_FAULT_KIND="K5")
        ratio = K5_RELEASE_RATIO[timing]
        label = f"worker A, B Step2 끝에서 대기, A 진행 {ratio:.0%} 에서 B 재개, lease={K5_LEASE_SECONDS}s"
        second = SecondWorker(run_id, timing, pause_dir, pause_b, LOG_DIR / f"{run_id}-worker-b.log",
                              dict(gate, APP_RELIABILITY_FAULT_PAUSE_DIR=str(pause_b)))
    elif scenario_id == "S-010R":
        # 바로 앞 S-010 칸을 이어받는다
        with psycopg.connect(TARGET_PG, autocommit=True) as conn:
            row = conn.execute(
                """SELECT run_id, fault_point, fault_timing, fault_kind, verdict FROM reliability.run
                   WHERE scenario_id = 'S-010' ORDER BY started_at DESC LIMIT 1""").fetchone()
        if not row:
            sys.exit("S-010 을 먼저 돌린다")
        matrix_cell = row
        cell = row[1:4]
        junit_extra.update(JOB1_FAULT_POINT=row[1], JOB1_FAULT_TIMING=row[2], JOB1_FAULT_KIND=row[3])

    with psycopg.connect(TARGET_PG, autocommit=True) as conn:
        conn.execute(
            """INSERT INTO reliability.run (run_id, scenario_id, dataset_id, seed, fault_point, fault_timing, fault_kind, git_commit)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
            (run_id, scenario_id, dataset_id, seed, *cell, commit),
        )
        if spec["clean"]:
            clean_month(conn)
            event(conn, run_id, "CLEAN", "2026-01 정산 결과 비움")
        elif scenario_id.endswith(("R", "E")):
            if (matrix_cell and matrix_cell[3] != "K1") or spec.get("wait_lease"):
                wait_lease_expiry(conn, run_id)
            event(conn, run_id, "RERUN_START", f"cutoff={CUTOFF}")

        if second and not prepare_step3(conn, run_id):
            precondition = "Step0 ~ Step2 준비 실행 뒤 TARGET 만 남지 않음"
        sampler = Sampler(run_id, spec["fault"])
        sampler.start()
        time.sleep(2)
        log_path = LOG_DIR / f"{run_id}.log"
        if spec.get("matrix") and fault_kind["proxy"]:
            watcher = PauseWatcher(pause_dir, fault_kind["proxy"], run_id, label)
            watcher.start()
        if second:
            second.start()
        try:
            exit_code = run_worker(conn, run_id, spec["fault"], log_path, worker_extra, label)
        finally:
            if watcher:
                watcher.stopped.set()
            if second:
                second.join()
            enable_all_proxies()
        injected = None
        if spec.get("matrix"):
            if args.kind == "K1":
                injected = f"Injected Job1 fault at {args.point}" in log_path.read_text(errors="ignore")
            elif args.kind == "K2":
                # Runtime.halt(137)
                injected = exit_code == 137
                if injected:
                    event(conn, run_id, "FAULT_INJECTED", f"{label} halt")
            else:
                # 연결을 끊었는데 worker 가 정상 종료했으면 장애가 결과에 닿지 않은 것이다
                injected = watcher.triggered.is_set() and exit_code != 0
            if not injected:
                event(conn, run_id, "FAULT_NOT_TRIGGERED", label)
            elif args.kind == "K1":
                event(conn, run_id, "FAULT_INJECTED", label)
        if spec.get("lease_fault"):
            if exit_code == 137:
                event(conn, run_id, "FAULT_INJECTED", f"{label} halt")
            else:
                precondition = f"P3a 강제 종료가 일어나지 않음 (exit={exit_code})"
        if scenario_id == "S-002E":
            # lease 안쪽에서 끝났는지 본다. 끝나기 전에 lease 가 지났으면 만료 전 재실행을 본 것이 아니다
            remaining = lease_remaining(conn)
            event(conn, run_id, "LEASE_REMAINING", "PROCESSING 없음" if remaining is None else f"{remaining:.0f}초")
            if remaining is None or remaining <= 0:
                precondition = "재실행이 끝났을 때 lease 안쪽 PROCESSING 이 없음"
        if second:
            second_exit = second.proc.returncode if second.proc else None
            if not second.released.is_set() and not precondition:
                precondition = f"A, B 가 Step2 끝에서 함께 멈추지 않음 (B exit={second_exit})"
        if precondition:
            event(conn, run_id, "PRECONDITION_FAILED", precondition)
        if scenario_id == "S-001" and hits_file.exists():
            # 이 데이터셋의 지점별 통과 횟수를 남겨 둔다
            hits_path(dataset_id).write_text(hits_file.read_text())
        time.sleep(3)
        sampler.stopped.set()
        sampler.join()

        event(conn, run_id, "VALIDATION", spec["test"].rsplit(".", 1)[1])
        junit_started = time.time()
        tests = [spec["test"]]
        if scenario_id == "S-001" and dataset_id == "D2-boundary":
            tests.append(BOUNDARY_TEST)
        junit_code = run_junit(tests, dataset_id, junit_extra)
        record_checks(conn, run_id, scenario_id, spec, junit_started)
        if second and not precondition:
            check_path = RESULT_DIR / f"{scenario_id}-{spec['name']}" / "lease-check.json"
            workers = json.loads(check_path.read_text())["metrics"]["workers"] if check_path.exists() else {}
            event(conn, run_id, "WORKER_SHARE", json.dumps(workers))
            # 같이 출발한 칸에서 한 worker 가 혼자 다 계산했으면 경합을 본 것이 아니다
            # MIDDLE, LAST 는 늦게 들어온 B 가 할 일이 없을 수 있어 몇 건씩 했는지만 남긴다
            if cell[1] == "FIRST" and len(workers) < 2:
                precondition = f"한 worker 가 혼자 계산함 {workers}"
                event(conn, run_id, "PRECONDITION_FAILED", precondition)
        verdict = "PASS" if junit_code == 0 else "FAIL"
        if injected is False or precondition:
            # 장애가 들어가지 않았으면 그 칸은 검증한 것이 아니다
            verdict = "FAIL"
        conn.execute(
            "UPDATE reliability.run SET finished_at = now(), job_exit_code = %s, verdict = %s WHERE run_id = %s",
            (exit_code, verdict, run_id),
        )
        if matrix_cell:
            previous_run, *previous_cell, previous_verdict = matrix_cell
            status = "PASS" if previous_verdict == "PASS" and verdict == "PASS" else "FAIL"
            conn.execute(
                """UPDATE reliability.fault_cell SET status = %s, last_run_id = %s, note = %s, updated_at = now()
                   WHERE fault_point = %s AND fault_timing = %s AND fault_kind = %s""",
                (status, run_id, f"장애 {previous_run} {previous_verdict}, 재실행 {verdict}", *previous_cell),
            )
        elif spec.get("concurrent"):
            conn.execute(
                """UPDATE reliability.fault_cell SET status = %s, last_run_id = %s, note = %s, updated_at = now()
                   WHERE fault_point = %s AND fault_timing = %s AND fault_kind = %s""",
                (verdict, run_id, f"worker A exit={exit_code}, worker B exit={second_exit}" + (f", {precondition}" if precondition else ""), *cell),
            )
        elif spec.get("cell"):
            conn.execute(
                """UPDATE reliability.fault_cell SET status = %s, last_run_id = %s, updated_at = now()
                   WHERE fault_point = %s AND fault_timing = %s AND fault_kind = %s""",
                (verdict, run_id, *spec["cell"]),
            )
        print(f"{run_id} worker exit={exit_code} junit={verdict}")
        sys.exit(0 if verdict == "PASS" else 1)


if __name__ == "__main__":
    main()
