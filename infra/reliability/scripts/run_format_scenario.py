# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2", "pymongo>=4.8"]
# ///
"""formatJob(Job2) 재실행 안전성 시나리오 (TR1L-181).

Job1 이 2026-01 을 마감한 뒤에 돌린다(S-001 이나 S-010R 다음). worker 는 운영 task 정의처럼
같은 cutoff, run.id 없이 띄운다. 그래서 같은 JobInstance 를 restart 한다.

F-001  청구서 생성 정상 실행. 비교 기준
F-002  청구서 생성 도중 worker 강제 종료 (SIGKILL). 발송 대상(READY)이 절반을 넘으면 죽인다
F-002E lease 가 끝나기 전 재실행. 다른 worker 가 돌고 있을 수 있어 시작하지 않아야 한다 (종료 코드 2)
F-002R lease 가 끝난 뒤 재실행. 남은 실행을 정리하고 처리한 사용자 다음부터 이어서 끝내야 한다

uv run infra/reliability/scripts/run_format_scenario.py F-001
uv run infra/reliability/scripts/run_format_scenario.py F-002 --chain J2-KILL
uv run infra/reliability/scripts/run_format_scenario.py F-002E --chain J2-KILL
uv run infra/reliability/scripts/run_format_scenario.py F-002R --chain J2-KILL
"""
import argparse
import os
import subprocess
import sys
import threading
import time
import urllib.request
from datetime import datetime
from pathlib import Path

import psycopg

import run_scenario as rs

BILLING_MONTH = rs.BILLING_MONTH_DAY
S3_ENDPOINT = os.getenv("REL_S3", "http://localhost:14566")
# worker(AWS SDK)는 가상 호스트 방식으로 버킷을 붙인다. *.localhost.localstack.cloud 는 127.0.0.1 로 풀린다
S3_WORKER_ENDPOINT = os.getenv("REL_S3_WORKER", "http://s3.localhost.localstack.cloud:14566")
S3_BUCKET = "tr1l-rel-bills"
# F-002 에서 짧게 줘서 F-002R 이 오래 기다리지 않게 한다. 운영 기본은 3600초
# F-002E 는 이 안에 시작해야 해서 강제 종료, 검증, worker 기동 시간보다 넉넉히 준다
FAULT_LEASE_SECONDS = 180
KILL_RATIO = 0.5
SAMPLE_INTERVAL = 0.25

SCENARIOS = {
    "F-001": dict(name="format-normal-run", clean=True),
    "F-002": dict(name="format-kill", clean=True, kill=True),
    "F-002E": dict(name="format-rerun-before-lease-expiry", clean=False, lease_alive=True),
    "F-002R": dict(name="format-rerun-after-lease-expiry", clean=False, wait_lease=True),
}

FORMAT_STATE_SQL = """
SELECT count(*) FILTER (WHERE bt.send_status = 'INIT'),
       count(*) FILTER (WHERE bt.send_status = 'READY'),
       (SELECT status FROM format_cycle WHERE billing_month = %(month)s)
FROM billing_targets bt
WHERE bt.billing_month = %(month)s
"""

# 이 cutoff 의 formatJob 실행들. 같은 JobInstance 를 restart 하면 여기 실행이 쌓인다
EXECUTIONS_SQL = """
SELECT je.job_execution_id, je.status, je.exit_code,
       coalesce((SELECT sum(se.write_count) FROM batch_step_execution se
                 WHERE se.job_execution_id = je.job_execution_id AND se.step_name = 'billingSnapShotStep'), 0)
FROM batch_job_execution je
JOIN batch_job_instance ji ON ji.job_instance_id = je.job_instance_id
WHERE ji.job_name = 'formatJob'
ORDER BY je.job_execution_id
"""


def format_state(conn):
    return conn.execute(FORMAT_STATE_SQL, {"month": BILLING_MONTH}).fetchone()


def executions():
    with psycopg.connect(rs.MAIN_PG, autocommit=True) as main:
        return main.execute(EXECUTIONS_SQL).fetchall()


def ensure_bucket():
    request = urllib.request.Request(f"{S3_ENDPOINT}/{S3_BUCKET}", method="PUT")
    urllib.request.urlopen(request, timeout=10).read()


def clean_format(conn):
    """청구서 생성 결과만 비운다. Job1 결과(billing_targets 행, snapshot)는 그대로 둔다"""
    conn.execute("UPDATE billing_targets SET send_status = 'INIT', s3_url_jsonb = NULL WHERE billing_month = %s",
                 (BILLING_MONTH,))
    conn.execute("DELETE FROM format_cycle WHERE billing_month = %s", (BILLING_MONTH,))
    with psycopg.connect(rs.MAIN_PG, autocommit=True) as main:
        main.execute("""
            WITH ji AS (SELECT job_instance_id FROM batch_job_instance WHERE job_name = 'formatJob'),
                 je AS (SELECT job_execution_id FROM batch_job_execution WHERE job_instance_id IN (SELECT * FROM ji)),
                 se AS (SELECT step_execution_id FROM batch_step_execution WHERE job_execution_id IN (SELECT * FROM je)),
                 d1 AS (DELETE FROM batch_step_execution_context WHERE step_execution_id IN (SELECT * FROM se)),
                 d2 AS (DELETE FROM batch_job_execution_context WHERE job_execution_id IN (SELECT * FROM je)),
                 d3 AS (DELETE FROM batch_job_execution_params WHERE job_execution_id IN (SELECT * FROM je))
            SELECT 1""")
        main.execute("""DELETE FROM batch_step_execution WHERE job_execution_id IN
                        (SELECT job_execution_id FROM batch_job_execution WHERE job_instance_id IN
                         (SELECT job_instance_id FROM batch_job_instance WHERE job_name = 'formatJob'))""")
        main.execute("""DELETE FROM batch_job_execution WHERE job_instance_id IN
                        (SELECT job_instance_id FROM batch_job_instance WHERE job_name = 'formatJob')""")
        main.execute("DELETE FROM batch_job_instance WHERE job_name = 'formatJob'")


class FormatSampler(threading.Thread):
    """worker 가 도는 동안 청구서 생성 진행을 센다. kill_at 이 있으면 READY 가 그만큼 되면 worker 를 죽인다"""

    def __init__(self, run_id, proc, kill_at=None):
        super().__init__(daemon=True)
        self.run_id, self.proc, self.kill_at = run_id, proc, kill_at
        self.killed_at_ready = None
        self.stopped = threading.Event()

    def run(self):
        with psycopg.connect(rs.TARGET_PG, autocommit=True) as conn:
            while not self.stopped.is_set():
                init, ready, status = format_state(conn)
                conn.execute("""INSERT INTO reliability.format_sample (run_id, init_count, ready_count, format_status)
                                VALUES (%s, %s, %s, %s) ON CONFLICT DO NOTHING""", (self.run_id, init, ready, status))
                if self.kill_at and self.killed_at_ready is None and ready >= self.kill_at and self.proc.poll() is None:
                    self.proc.kill()  # SIGKILL. shutdown hook 도 돌지 않는다
                    self.killed_at_ready = ready
                    rs.event(conn, self.run_id, "FAULT_INJECTED", f"READY {ready} 에서 SIGKILL")
                self.stopped.wait(SAMPLE_INTERVAL)


def worker_cmd(jar):
    return [f"{rs.JAVA_HOME}/bin/java", "-Xmx2g", "-jar", str(jar),
            "--spring.batch.job.name=formatJob", f"--batch.job2StartTime={rs.CUTOFF}",
            f"--server.port={rs.WORKER_PORT}"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scenario", choices=list(SCENARIOS))
    parser.add_argument("--chain", help="F-002 → F-002E → F-002R 을 한 묶음으로 남길 ID (reliability.run.chain_id)")
    parser.add_argument("--jar", default=str(rs.WORKER_JAR), help="고치기 전 jar 로 돌릴 때 준다")
    parser.add_argument("--expire-lease", action="store_true",
                        help="F-002R 에서 기다리지 않고 format_cycle lease 를 지난 시각으로 당긴다. "
                             "lease 가 1시간 고정인 고치기 전 jar 로 lease 만료 뒤를 볼 때 쓴다")
    args = parser.parse_args()
    scenario_id, spec = args.scenario, SCENARIOS[args.scenario]

    rs.LOG_DIR.mkdir(parents=True, exist_ok=True)
    run_id = f"{scenario_id}-{datetime.now():%Y%m%d-%H%M%S}"
    log_path = rs.LOG_DIR / f"{run_id}.log"
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, cwd=rs.ROOT).stdout.strip()
    dataset_id, seed = rs.loaded_dataset()
    checks = []  # (check_id, passed, violation_count, detail)

    def check(check_id, passed, detail, violations=None):
        checks.append((check_id, bool(passed), violations if violations is not None else (0 if passed else 1), detail))

    with psycopg.connect(rs.TARGET_PG, autocommit=True) as conn:
        conn.execute(
            """INSERT INTO reliability.run (run_id, scenario_id, dataset_id, seed, git_commit, chain_id)
               VALUES (%s, %s, %s, %s, %s, %s)""",
            (run_id, scenario_id, dataset_id, seed, commit, args.chain),
        )
        cycle = conn.execute("SELECT status FROM billing_cycle WHERE billing_month = %s", (BILLING_MONTH,)).fetchone()
        if not cycle or cycle[0] != "FINISHED":
            sys.exit("Job1 이 2026-01 을 마감하지 않았다. S-001 을 먼저 돌린다")
        total = conn.execute("SELECT count(*) FROM billing_targets WHERE billing_month = %s", (BILLING_MONTH,)).fetchone()[0]
        ensure_bucket()
        # 운영 DB 에는 format_cycle 이 먼저 만들어져 있다. 고치기 전 jar 는 이 테이블을 만들지 않아서 여기서 맞춘다
        conn.execute((rs.ROOT / "apps/worker/src/main/resources/db/sql/target/format_cycle.sql").read_text())

        if spec["clean"]:
            clean_format(conn)
            rs.event(conn, run_id, "CLEAN", "2026-01 청구서 생성 결과와 formatJob 실행 기록 비움")
        before_state = format_state(conn)
        before_executions = executions()
        if spec.get("wait_lease"):
            if args.expire_lease:
                conn.execute("UPDATE format_cycle SET lease_until = now() - interval '1 second' WHERE billing_month = %s",
                             (BILLING_MONTH,))
                rs.event(conn, run_id, "LEASE_EXPIRED", "format_cycle lease 를 지난 시각으로 당김")
            else:
                while (left := conn.execute("SELECT extract(epoch FROM lease_until - now()) FROM format_cycle "
                                            "WHERE billing_month = %s AND status = 'RUNNING'",
                                            (BILLING_MONTH,)).fetchone()) and left[0] and left[0] > 0:
                    rs.event(conn, run_id, "LEASE_WAIT", f"{left[0]:.0f}초")
                    time.sleep(float(left[0]) + 1)
        if spec.get("lease_alive"):
            left = conn.execute("SELECT extract(epoch FROM lease_until - now()) FROM format_cycle WHERE billing_month = %s",
                                (BILLING_MONTH,)).fetchone()
            rs.event(conn, run_id, "LEASE_REMAINING", f"{left[0]:.0f}초" if left and left[0] is not None else "없음")
            # lease 가 지났으면 lease 안쪽 재실행을 본 것이 아니다
            check("F_LEASE_ALIVE", bool(left and left[0] and left[0] > 30), "시작할 때 format_cycle lease 가 30초 넘게 남음")
        rs.event(conn, run_id, "RERUN_START" if not spec["clean"] else "WORKER_START",
                 f"jar={Path(args.jar).name} cutoff={rs.CUTOFF}")

        extra = dict(
            BATCH_ADD_RUN_ID="false",  # 운영 task 정의처럼 같은 JobInstance 로 돌린다
            BATCH_JOB2_LEASE_SECONDS=str(FAULT_LEASE_SECONDS if spec.get("kill") else 3600),
            AWS_ENDPOINT_URL_S3=S3_WORKER_ENDPOINT,
            AWS_ACCESS_KEY_ID="test",
            AWS_SECRET_ACCESS_KEY="test",
            S3_BUCKET=S3_BUCKET,
        )
        with open(log_path, "w") as log:
            proc = subprocess.Popen(worker_cmd(args.jar), env=rs.worker_env(False, extra), stdout=log,
                                    stderr=subprocess.STDOUT, cwd=rs.ROOT)
            sampler = FormatSampler(run_id, proc, kill_at=int(total * KILL_RATIO) if spec.get("kill") else None)
            sampler.start()
            exit_code = proc.wait()
            time.sleep(1)
            sampler.stopped.set()
            sampler.join()
        rs.event(conn, run_id, "WORKER_EXIT", f"exit={exit_code}")

        init, ready, status = format_state(conn)
        after_executions = executions()
        rs.event(conn, run_id, "FORMAT_STATE", f"INIT {init}, READY {ready}, format_cycle {status}")
        rs.event(conn, run_id, "EXECUTIONS", ", ".join(f"#{e[0]} {e[1]} write={e[3]}" for e in after_executions))
        missing_s3 = conn.execute("""SELECT count(*) FROM billing_targets WHERE billing_month = %s AND send_status = 'READY'
                                     AND (s3_url_jsonb IS NULL OR jsonb_array_length(s3_url_jsonb) <> 2)""",
                                  (BILLING_MONTH,)).fetchone()[0]

        if scenario_id == "F-001" or scenario_id == "F-002R":
            written = sum(e[3] for e in after_executions)
            check("F_EXIT", exit_code == 0, f"종료 코드 {exit_code}")
            check("F_CYCLE", status == "FINISHED", f"format_cycle {status}")
            check("F_READY", ready == total and init == 0, f"READY {ready} / 대상 {total}, INIT {init}", total - ready)
            check("F_S3", missing_s3 == 0, f"S3 주소가 없거나 채널 2개가 아닌 READY {missing_s3}", missing_s3)
            # restart 가 처리한 사용자 다음부터 이어서 읽었으면 모든 실행의 쓰기 합이 대상 수와 같다
            check("F_WRITE_ONCE", written == total, f"모든 실행의 billingSnapShotStep 쓰기 합 {written} / 대상 {total}",
                  abs(written - total))
            if scenario_id == "F-002R":
                left_running = [e for e in after_executions if e[1] in ("STARTING", "STARTED", "STOPPING")]
                check("F_NO_STARTED", not left_running, f"남은 STARTED 실행 {[e[0] for e in left_running]}",
                      len(left_running))
                check("F_RESUMED", len(after_executions) == len(before_executions) + 1,
                      f"같은 JobInstance 실행 수 {len(before_executions)} → {len(after_executions)}")
        elif scenario_id == "F-002":
            killed = sampler.killed_at_ready is not None and exit_code in (-9, 137)
            check("F_KILLED", killed, f"READY {sampler.killed_at_ready} 에서 SIGKILL, 종료 코드 {exit_code}")
            check("F_PARTIAL", 0 < ready < total, f"READY {ready} / 대상 {total}")
            check("F_CYCLE", status == "RUNNING", f"format_cycle {status}")
            left = [e for e in after_executions if e[1] == "STARTED"]
            check("F_LEFT_STARTED", len(left) == 1, f"STARTED 로 남은 실행 {[e[0] for e in left]}")
        elif scenario_id == "F-002E":
            check("F_EXIT", exit_code == 2, f"종료 코드 {exit_code} (시작하지 않았으면 2)")
            check("F_UNCHANGED", (init, ready, status) == tuple(before_state),
                  f"전 {tuple(before_state)} → 후 {(init, ready, status)}")
            check("F_EXECUTIONS_KEPT", [e[:2] for e in after_executions] == [e[:2] for e in before_executions],
                  f"실행 {[e[:2] for e in before_executions]} → {[e[:2] for e in after_executions]}")

        rs.event(conn, run_id, "VALIDATION", ", ".join(f"{c[0]}={'PASS' if c[1] else 'FAIL'}" for c in checks))
        for check_id, passed, violations, detail in checks:
            rs.insert_check(conn, run_id, check_id, passed, violations, detail)
        verdict = "PASS" if checks and all(c[1] for c in checks) else "FAIL"
        conn.execute("UPDATE reliability.run SET finished_at = now(), job_exit_code = %s, verdict = %s WHERE run_id = %s",
                     (exit_code, verdict, run_id))
        print(f"{run_id} worker exit={exit_code} verdict={verdict}")
        for check_id, passed, _, detail in checks:
            print(f"  {check_id:18} {'PASS' if passed else 'FAIL'}  {detail}")
        sys.exit(0 if verdict == "PASS" else 1)


if __name__ == "__main__":
    main()
