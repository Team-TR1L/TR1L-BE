# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2"]
# ///
"""규모가 결과를 바꾸는지 보는 핵심 칸을 지금 적재된 데이터셋에서 돈다 (TR1L-174).

chunk 수, partition 크기, lease 시간이 커지면 달라질 수 있는 칸만 고른다.
  P3a·MIDDLE·K1  claim 직후 예외
  P3c·MIDDLE·K1  Mongo 저장 직후, 상태 갱신 전 예외
  P3a·MIDDLE·K2  claim 직후 강제 종료. 커밋된 PROCESSING 이 lease 동안 남는다
  K5·FIRST       worker 두 개가 Step3 를 같이 돈다
칸마다 장애 직후 상태(CALCULATED, PROCESSING, 결과만 있는 work)와 마지막 판정, 시간과 자원 사용량을 남긴다.
D5 와 D3 에서 각각 돌려 build/reliability-scale/<데이터셋>.json 을 비교한다.

REL_SAMPLE_INTERVAL=2 uv run infra/reliability/scripts/run_scale.py          # 지금 적재된 데이터셋
uv run infra/reliability/scripts/run_scale.py --compare D3-pairwise D5-scale  # 두 결과를 표로
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

import psycopg

sys.path.insert(0, str(Path(__file__).parent))
import resource_report  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
SCRIPTS = ROOT / "infra/reliability/scripts"
OUT = ROOT / "build/reliability-scale"
TARGET_PG = "postgresql://postgres:postgres@localhost:25433/billing_target"

CELLS = [
    ("P3a", "MIDDLE", "K1"),
    ("P3c", "MIDDLE", "K1"),
    ("P3a", "MIDDLE", "K2"),
    ("P3a", "FIRST", "K5"),
]


def scenario(*args):
    result = subprocess.run(["uv", "run", "-q", str(SCRIPTS / "run_scenario.py"), *args],
                            cwd=ROOT, capture_output=True, text=True)
    last = (result.stdout.strip().splitlines() or ["(출력 없음)"])[-1]
    print(f"  {' '.join(args):<44} {last}", flush=True)
    return last.split()[0] if last.startswith("S-") else None


def after_fault_state(conn, run_id):
    """장애 실행이 끝났을 때 마지막 상태 표본"""
    row = conn.execute(
        """SELECT target_count, processing_count, calculated_count, snapshot_count, snapshot_only_count
           FROM reliability.state_sample WHERE run_id = %s ORDER BY sampled_at DESC LIMIT 1""", (run_id,)).fetchone()
    keys = ("target", "processing", "calculated", "snapshot", "snapshot_only")
    return dict(zip(keys, row)) if row else None


def checks(conn, run_id):
    return {check: passed for check, passed in conn.execute(
        "SELECT check_id, passed FROM reliability.check_result WHERE run_id = %s ORDER BY check_id", (run_id,))}


def run_cells(dataset):
    results = []
    for point, timing, kind in CELLS:
        print(f"{dataset} {point}·{timing}·{kind}", flush=True)
        if kind == "K5":
            run_ids = [scenario("S-011", "--timing", timing)]
        else:
            run_ids = [scenario("S-010", "--point", point, "--timing", timing, "--kind", kind), scenario("S-010R")]
        with psycopg.connect(TARGET_PG) as conn:
            verdicts = {run_id: conn.execute("SELECT verdict FROM reliability.run WHERE run_id = %s",
                                             (run_id,)).fetchone()[0] for run_id in run_ids if run_id}
            results.append(dict(
                cell=f"{point}·{timing}·{kind}",
                run_ids=run_ids,
                verdict="PASS" if run_ids and None not in run_ids and all(v == "PASS" for v in verdicts.values()) else "FAIL",
                after_fault=after_fault_state(conn, run_ids[0]) if kind != "K5" and run_ids[0] else None,
                final_checks=checks(conn, run_ids[-1]) if run_ids[-1] else {},
                resources=[resource_report.report(run_id) for run_id in run_ids if run_id],
            ))
    return results


def compare(small, large):
    a = json.loads((OUT / f"{small}.json").read_text())
    b = json.loads((OUT / f"{large}.json").read_text())
    print(f"| 칸 | {small} 판정 | {large} 판정 | 장애 직후 PROCESSING ({small} / {large}) | "
          f"결과만 있는 work | 실행 시간 초 | heap 최대 MB |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for x, y in zip(a["cells"], b["cells"]):
        fx, fy = x["after_fault"] or {}, y["after_fault"] or {}
        sx = sum(r["seconds"] for r in x["resources"])
        sy = sum(r["seconds"] for r in y["resources"])
        hx = max((r["resources"]["heap_max_mb"] or 0) for r in x["resources"])
        hy = max((r["resources"]["heap_max_mb"] or 0) for r in y["resources"])
        print(f"| {x['cell']} | {x['verdict']} | {y['verdict']} | {fx.get('processing', '-')} / {fy.get('processing', '-')} | "
              f"{fx.get('snapshot_only', '-')} / {fy.get('snapshot_only', '-')} | {sx} / {sy} | {hx} / {hy} |")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--compare", nargs=2, metavar=("SMALL", "LARGE"))
    parser.add_argument("--baseline-run", help="이미 돌린 S-001 의 run_id. 주면 S-001 을 다시 돌리지 않는다")
    args = parser.parse_args()
    if args.compare:
        compare(*args.compare)
        return

    dataset = (ROOT / "build/reliability-dataset").read_text().splitlines()[0]
    baseline = args.baseline_run
    if not baseline:
        print(f"{dataset} 기준 실행", flush=True)
        baseline = scenario("S-001")
    cells = run_cells(dataset)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{dataset}.json").write_text(json.dumps(dict(
        dataset=dataset,
        baseline=resource_report.report(baseline) if baseline else None,
        cells=cells), ensure_ascii=False, indent=2, default=str))
    failed = [c["cell"] for c in cells if c["verdict"] != "PASS"]
    print("실패한 칸:", ", ".join(failed) if failed else "없음")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
