# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2"]
# ///
"""D4 seed 와 장애 지점·시점·종류를 무작위로 골라 N 번 돌린다 (TR1L-173).

한 번(iteration)은 장애 → (30% 확률로 재실행 중 장애 한 번 더) → 마지막 재실행이다.
D4 seed 하나마다 데이터셋을 다시 적재하고 S-001 기준을 만든 뒤, 그 위에서 여러 번 돈다.
고르는 값은 모두 --seed 하나에서 나오므로 같은 --seed 면 같은 순서로 다시 돈다.

결과
  - 실행마다 reliability.run.chain_id = RND-<seed>-<번호>
  - 한 번마다 scenario_id = RANDOM 인 요약 행 (PASS, FAIL, INVALID). 대시보드 실패율 상한이 이 행을 센다
  - build/reliability-random/random-<seed>.json, .md 리포트. 실패한 번에는 그대로 다시 돌리는 명령을 적는다
  - INVALID 는 장애가 들어가지 않는 등 전제가 깨진 번이다. 시스템 실패가 아니라 N 에서 뺀다

uv run infra/reliability/scripts/run_random.py --seed 20261022 --datasets 3 --per-dataset 10
uv run infra/reliability/scripts/run_random.py --seed 20261022 --datasets 3 --per-dataset 10 --only 17
"""

import argparse
import json
import random
import subprocess
import sys
import time
from pathlib import Path

import psycopg

ROOT = Path(__file__).resolve().parents[3]
SCRIPTS = ROOT / "infra/reliability/scripts"
REPORT_DIR = ROOT / "build/reliability-random"
TARGET_PG = "postgresql://postgres:postgres@localhost:25433/billing_target"

POINTS = ["P0", "P1", "P2", "P3a", "P3b", "P3c", "P3d", "P4"]
TIMINGS = ["FIRST", "MIDDLE", "LAST"]
KINDS = ["K1", "K2", "K3", "K4"]
TASKLET_POINTS = {"P0", "P4"}
EXCLUDED = {("P4", "K4")}  # Step4 는 MongoDB 를 쓰지 않는다
SECOND_FAULT_RATE = 0.3
INVALID_EVENTS = ("FAULT_NOT_TRIGGERED", "PRECONDITION_FAILED")


def pick_fault(rng, timing=None):
    while True:
        point, kind = rng.choice(POINTS), rng.choice(KINDS)
        if (point, kind) not in EXCLUDED:
            break
    timing = "FIRST" if point in TASKLET_POINTS else (timing or rng.choice(TIMINGS))
    return point, timing, kind


def plan(seed, datasets, per_dataset):
    """--seed 에서 데이터셋 seed 와 번마다의 장애를 정한다. 같은 seed 면 같은 계획이다"""
    rng = random.Random(seed)
    iterations = []
    for d in range(datasets):
        dataset_seed = rng.randrange(1, 1_000_000)
        for _ in range(per_dataset):
            faults = [pick_fault(rng)]
            # 재실행은 지점을 지나는 횟수가 적어서 두 번째 장애는 FIRST 로 둔다
            if rng.random() < SECOND_FAULT_RATE:
                faults.append(pick_fault(rng, timing="FIRST"))
            iterations.append(dict(number=len(iterations) + 1, dataset_seed=dataset_seed, faults=faults))
    return iterations


def scenario_commands(iteration, chain_id):
    commands = []
    for index, (point, timing, kind) in enumerate(iteration["faults"]):
        scenario = "S-010" if index == 0 else "S-012"
        commands.append([scenario, "--point", point, "--timing", timing, "--kind", kind, "--chain", chain_id])
    commands.append(["S-010R" if len(iteration["faults"]) == 1 else "S-012R", "--chain", chain_id])
    return commands


def repro(iteration):
    """실패한 번을 그대로 다시 돌리는 명령"""
    lines = [f"infra/reliability/scripts/setup_local.sh D4-random {iteration['dataset_seed']}",
             "uv run infra/reliability/scripts/run_scenario.py S-001"]
    for command in scenario_commands(iteration, "repro"):
        lines.append("uv run infra/reliability/scripts/run_scenario.py " + " ".join(command[:-2]))
    return lines


def setup_dataset(dataset_seed):
    started = time.monotonic()
    log = ROOT / f"build/setup-D4-{dataset_seed}.log"
    with open(log, "w") as out:
        code = subprocess.run([str(SCRIPTS / "setup_local.sh"), "D4-random", str(dataset_seed)],
                              cwd=ROOT, stdout=out, stderr=subprocess.STDOUT).returncode
    if code != 0:
        sys.exit(f"D4-random {dataset_seed} 적재 실패. {log} 를 본다")
    if scenario("S-001")[0] != 0:
        sys.exit(f"D4-random {dataset_seed} 기준 실행 S-001 실패")
    return time.monotonic() - started


def scenario(*args):
    result = subprocess.run(["uv", "run", "-q", str(SCRIPTS / "run_scenario.py"), *args],
                            cwd=ROOT, capture_output=True, text=True)
    last = (result.stdout.strip().splitlines() or ["(출력 없음)"])[-1]
    print(f"    {' '.join(args):<66} {last}", flush=True)
    return result.returncode, last.split()[0] if last.startswith("S-") else None


def judge(conn, run_ids):
    if None in run_ids:
        return "INVALID", "실행 ID 를 읽지 못함"
    invalid = conn.execute(
        "SELECT run_id, event_type FROM reliability.run_event WHERE run_id = ANY(%s) AND event_type = ANY(%s)",
        (run_ids, list(INVALID_EVENTS))).fetchall()
    if invalid:
        return "INVALID", f"{invalid[0][0]} {invalid[0][1]}"
    failed = conn.execute(
        """SELECT r.run_id, string_agg(c.check_id, ',') FILTER (WHERE NOT c.passed)
           FROM reliability.run r LEFT JOIN reliability.check_result c USING (run_id)
           WHERE r.run_id = ANY(%s) AND r.verdict <> 'PASS' GROUP BY r.run_id""", (run_ids,)).fetchall()
    if failed:
        return "FAIL", "; ".join(f"{run_id} {checks or 'JUnit 실패'}" for run_id, checks in failed)
    return "PASS", "모든 실행 통과"


def upper_bound(passed, failed):
    n = passed + failed
    if n == 0:
        return None
    return min(100.0, round(300.0 / n, 1)) if failed == 0 else None


def write_report(seed, results, setup_seconds):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    passed = sum(r["verdict"] == "PASS" for r in results)
    failed = sum(r["verdict"] == "FAIL" for r in results)
    invalid = sum(r["verdict"] == "INVALID" for r in results)
    durations = [r["seconds"] for r in results]
    summary = dict(seed=seed, valid=passed + failed, passed=passed, failed=failed, invalid=invalid,
                   upper_bound_percent=upper_bound(passed, failed),
                   average_seconds=round(sum(durations) / len(durations), 1) if durations else None,
                   dataset_setup_seconds=setup_seconds)
    (REPORT_DIR / f"random-{seed}.json").write_text(
        json.dumps(dict(summary=summary, iterations=results), ensure_ascii=False, indent=2))

    lines = [f"# Job1 무작위 반복 실행 (seed {seed})", "",
             f"- 유효 {summary['valid']}번 중 실패 {failed}번, 전제가 깨진 번 {invalid}번",
             f"- 실패율 95% 상한 {summary['upper_bound_percent']}% (rule of three, 실패가 없을 때만)"
             if failed == 0 else f"- 실패율 {round(100 * failed / max(summary['valid'], 1), 1)}%",
             f"- 한 번 평균 {summary['average_seconds']}초, 데이터셋 적재와 S-001 평균 "
             f"{round(sum(setup_seconds.values()) / max(len(setup_seconds), 1))}초", "",
             "| 번 | D4 seed | 장애 | 결과 | 초 |", "| --- | --- | --- | --- | --- |"]
    for r in results:
        faults = " → ".join("·".join(f) for f in r["faults"])
        lines.append(f"| {r['number']} | {r['dataset_seed']} | {faults} | {r['verdict']} | {r['seconds']} |")
    for r in results:
        if r["verdict"] != "PASS":
            lines += ["", f"## {r['number']}번 {r['verdict']}", "", r["detail"], "", "```bash", *r["repro"], "```"]
    (REPORT_DIR / f"random-{seed}.md").write_text("\n".join(lines) + "\n")
    return summary


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--datasets", type=int, default=3, help="D4 seed 개수")
    parser.add_argument("--per-dataset", type=int, default=10, help="D4 seed 하나에서 돌릴 번 수")
    parser.add_argument("--only", type=int, nargs="*", help="이 번호만 다시 돈다")
    parser.add_argument("--keep-dataset", action="store_true", help="끝나고 D1 으로 되돌리지 않는다")
    args = parser.parse_args()

    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, cwd=ROOT).stdout.strip()
    iterations = [i for i in plan(args.seed, args.datasets, args.per_dataset)
                  if not args.only or i["number"] in args.only]
    results, setup_seconds, loaded = [], {}, None
    for iteration in iterations:
        if iteration["dataset_seed"] != loaded:
            print(f"D4-random {iteration['dataset_seed']} 적재와 S-001", flush=True)
            setup_seconds[iteration["dataset_seed"]] = round(setup_dataset(iteration["dataset_seed"]))
            loaded = iteration["dataset_seed"]
        chain_id = f"RND-{args.seed}-{iteration['number']:03d}"
        print(f"  {chain_id} " + " → ".join("·".join(f) for f in iteration["faults"]), flush=True)
        started = time.monotonic()
        run_ids = [scenario(*command)[1] for command in scenario_commands(iteration, chain_id)]
        with psycopg.connect(TARGET_PG, autocommit=True) as conn:
            verdict, detail = judge(conn, run_ids)
            point, timing, kind = iteration["faults"][0]
            conn.execute(
                """INSERT INTO reliability.run (run_id, scenario_id, dataset_id, seed, fault_point, fault_timing,
                                               fault_kind, git_commit, chain_id, finished_at, verdict)
                   VALUES (%s, 'RANDOM', 'D4-random', %s, %s, %s, %s, %s, %s, now(), %s)
                   ON CONFLICT (run_id) DO UPDATE SET started_at = now(), finished_at = now(), verdict = EXCLUDED.verdict,
                                                      git_commit = EXCLUDED.git_commit""",
                (chain_id, iteration["dataset_seed"], point, timing, kind, commit, chain_id, verdict))
            conn.execute(
                """INSERT INTO reliability.check_result (run_id, check_id, passed, violation_count, detail)
                   VALUES (%s, 'RANDOM', %s, %s, %s)
                   ON CONFLICT (run_id, check_id) DO UPDATE SET passed = EXCLUDED.passed,
                       violation_count = EXCLUDED.violation_count, detail = EXCLUDED.detail""",
                (chain_id, verdict != "FAIL", int(verdict == "FAIL"),
                 " → ".join("·".join(f) for f in iteration["faults"]) + f" · {detail}"))
        results.append(dict(iteration, verdict=verdict, detail=detail, run_ids=run_ids,
                            seconds=round(time.monotonic() - started), repro=repro(iteration)))
        print(f"    → {verdict} {detail}", flush=True)

    summary = write_report(args.seed, results, setup_seconds)
    print(json.dumps(summary, ensure_ascii=False))
    if not args.keep_dataset:
        # 다른 시나리오는 D1 기준으로 돈다
        print("D1-rerun-mvp 로 되돌리고 S-001", flush=True)
        with open(ROOT / "build/setup-D1.log", "w") as out:
            subprocess.run([str(SCRIPTS / "setup_local.sh"), "D1-rerun-mvp"], cwd=ROOT, stdout=out, stderr=subprocess.STDOUT)
        scenario("S-001")
    sys.exit(1 if summary["failed"] else 0)


if __name__ == "__main__":
    main()
