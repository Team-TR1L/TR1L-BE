# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2", "pymongo>=4.8"]
# ///
"""재실행 안전 장치를 하나씩 깨뜨린 mutant(M1 ~ M6)로 장애 칸을 돌려 검증이 잡는지 본다.

mutant 는 원본 클래스를 복사해 한 곳만 바꾼 것이다 (apps/worker/src/reliabilityMutant).
run_scenario.py --mutant 로 worker 를 띄우면 그 클래스가 원본을 가린다. 운영 jar 에는 들어가지 않는다.

mutant 마다 칸 하나(장애 → 재실행)를 돌리고, 그 사이 검증이 하나라도 실패하면 잡은 것(killed)으로 본다.
결과는 reliability.check_result 의 check_id M1 ~ M6 으로 남고, 대시보드 mutation score 패널이 읽는다.

./gradlew :apps:worker:reliabilityMutantJars
uv run infra/reliability/scripts/run_mutants.py            # M1 ~ M6
uv run infra/reliability/scripts/run_mutants.py M3 M6
"""

import argparse
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import psycopg
from pymongo import MongoClient

sys.path.insert(0, str(Path(__file__).parent))
import run_scenario as rs  # noqa: E402

SCRIPT = Path(__file__).with_name("run_scenario.py")

# mutant 마다 깨뜨리는 장치, 잡아야 하는 조건(01-design.md 7.1), 돌릴 칸
MUTANTS = {
    "M1": dict(breaks="Step2 ON CONFLICT DO NOTHING 제거", expect="C2",
               runs=[["S-010", "--point", "P3c", "--timing", "MIDDLE"], ["S-010R"]]),
    "M2": dict(breaks="snapshot 을 upsert 대신 insert", expect="C4, C5",
               runs=[["S-010", "--point", "P3c", "--timing", "MIDDLE"], ["S-010R"]]),
    "M3": dict(breaks="lease 가 끝난 PROCESSING 재선점 제거", expect="C3",
               runs=[["S-002"], ["S-002R"]]),
    # 상태 갱신과 Mongo 저장 사이에서 Mongo 를 끊는다. 순서가 바뀌면 여기서 차이가 나야 한다
    "M4": dict(breaks="상태 갱신을 snapshot 저장보다 먼저", expect="C4",
               runs=[["S-010", "--point", "P3c", "--timing", "MIDDLE", "--kind", "K4"], ["S-010R"]]),
    "M5": dict(breaks="고정된 cutoff 대신 실행 시각", expect="C1",
               runs=[["S-010", "--point", "P3c", "--timing", "MIDDLE"], ["S-010R"]]),
    "M6": dict(breaks="약정 할인 내림을 올림으로", expect="C6",
               runs=[["S-010", "--point", "P3c", "--timing", "MIDDLE"], ["S-010R"]]),
}
# 장애가 들어가지 않았거나 시나리오 전제가 깨진 실행은 mutant 를 잡았다고 볼 수 없다
INVALID_EVENTS = ("FAULT_NOT_TRIGGERED", "PRECONDITION_FAILED")


def run(args):
    result = subprocess.run(["uv", "run", "-q", str(SCRIPT), *args], cwd=rs.ROOT, capture_output=True, text=True)
    last = (result.stdout.strip().splitlines() or ["(출력 없음)"])[-1]
    print(f"  {' '.join(args):<52} {last}", flush=True)
    run_id = last.split()[0] if last.startswith("S-") else None
    return run_id


def clean_other_month(conn):
    """M5 는 실행 시각 기준 전월을 정산한다. 그 월의 결과를 지운다"""
    now = datetime.now(timezone(timedelta(hours=9)))
    month = (now.replace(day=1) - timedelta(days=1)).replace(day=1).date()
    if month.isoformat() == rs.BILLING_MONTH_DAY:
        return
    for table, column in (("billing_work", "billing_month_day"), ("billing_targets", "billing_month"),
                          ("billing_cycle", "billing_month")):
        conn.execute(f"DELETE FROM {table} WHERE {column} = %s", (month,))
    MongoClient(rs.MONGO_URL)[rs.MONGO_DB]["billing_snapshot"].delete_many({"billingMonth": month.isoformat()})


def judge(conn, mutant, run_ids):
    """잡았으면 True, 못 잡았으면 False, 판정할 수 없으면 None 과 이유"""
    for run_id in run_ids:
        log = (rs.LOG_DIR / f"{run_id}.log").read_text(errors="ignore")
        if f"Job1 mutant {mutant} active" not in log:
            return None, f"{run_id} 에서 mutant 클래스가 로드되지 않음"
    invalid = conn.execute(
        "SELECT run_id, event_type, detail FROM reliability.run_event WHERE run_id = ANY(%s) AND event_type = ANY(%s)",
        (run_ids, list(INVALID_EVENTS))).fetchall()
    if invalid:
        return None, f"전제가 깨짐 {invalid[0]}"
    failed = conn.execute(
        """SELECT r.scenario_id, c.check_id FROM reliability.check_result c JOIN reliability.run r USING (run_id)
           WHERE c.run_id = ANY(%s) AND NOT c.passed ORDER BY r.started_at, c.check_id""", (run_ids,)).fetchall()
    verdicts = conn.execute("SELECT scenario_id, verdict FROM reliability.run WHERE run_id = ANY(%s) ORDER BY started_at",
                            (run_ids,)).fetchall()
    if failed:
        return True, "잡은 검증 " + ", ".join(f"{scenario} {check}" for scenario, check in failed)
    if any(verdict == "FAIL" for _, verdict in verdicts):
        # check_result 로 남지 않은 단언(예: 장애 직후 예상 상태)에서 실패했다
        return True, "JUnit 실패 " + ", ".join(f"{scenario} {verdict}" for scenario, verdict in verdicts)
    return False, "모든 검증 통과"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mutants", nargs="*", help="M1 ~ M6. 비우면 전부")
    args = parser.parse_args()
    args.mutants = args.mutants or list(MUTANTS)
    if unknown := set(args.mutants) - set(MUTANTS):
        parser.error(f"모르는 mutant {sorted(unknown)}")

    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, cwd=rs.ROOT).stdout.strip()
    dataset_id, seed = rs.loaded_dataset()
    summary = []
    for mutant in args.mutants:
        spec = MUTANTS[mutant]
        print(f"{mutant} {spec['breaks']} (잡아야 할 조건 {spec['expect']})", flush=True)
        run_ids = [run([*scenario, "--mutant", mutant]) for scenario in spec["runs"]]
        with psycopg.connect(rs.TARGET_PG, autocommit=True) as conn:
            if mutant == "M5":
                clean_other_month(conn)
            if None in run_ids:
                killed, detail = None, "실행 ID 를 읽지 못함"
            else:
                killed, detail = judge(conn, mutant, run_ids)
            run_id = f"MUT-{mutant}-{datetime.now():%Y%m%d-%H%M%S}"
            conn.execute(
                """INSERT INTO reliability.run (run_id, scenario_id, dataset_id, seed, git_commit, mutant, finished_at, verdict)
                   VALUES (%s, 'MUTANT', %s, %s, %s, %s, now(), %s)""",
                (run_id, dataset_id, seed, commit, mutant,
                 {True: "KILLED", False: "SURVIVED", None: "INVALID"}[killed]))
            if killed is not None:
                rs.insert_check(conn, run_id, mutant, killed, 0 if killed else 1,
                                f"{spec['breaks']} → {detail} (기대 {spec['expect']}, 실행 {', '.join(run_ids)})")
        label = {True: "잡음", False: "살아남음", None: "판정 불가"}[killed]
        print(f"  → {label}: {detail}", flush=True)
        summary.append((mutant, label))

    print("결과:", ", ".join(f"{mutant} {label}" for mutant, label in summary))
    sys.exit(0 if all(label != "판정 불가" for _, label in summary) else 1)


if __name__ == "__main__":
    main()
