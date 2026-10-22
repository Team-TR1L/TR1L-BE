"""장애 행렬의 K1(애플리케이션 예외) 칸을 차례로 돌린다.

S-001 을 한 번 돌려 기준과 지점별 통과 횟수를 만든 뒤,
칸마다 S-010(장애) → S-010R(재실행) 을 돌린다. 결과는 reliability.fault_cell 에 남는다.

python3 infra/reliability/scripts/run_fault_matrix.py
python3 infra/reliability/scripts/run_fault_matrix.py --points P2 P3c --timings FIRST LAST
"""

import argparse
import subprocess
import sys
from pathlib import Path

SCRIPT = Path(__file__).with_name("run_scenario.py")
ROOT = Path(__file__).resolve().parents[2].parent
POINTS = ["P0", "P1", "P2", "P3a", "P3b", "P3c", "P3d", "P4"]
TIMINGS = ["FIRST", "MIDDLE", "LAST"]
TASKLET_POINTS = {"P0", "P4"}


def run(*args):
    result = subprocess.run(["uv", "run", "-q", str(SCRIPT), *args], cwd=ROOT, capture_output=True, text=True)
    last = (result.stdout.strip().splitlines() or ["(출력 없음)"])[-1]
    print(f"  {' '.join(args):<32} {last}", flush=True)
    return result.returncode


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--points", nargs="+", default=POINTS, choices=POINTS)
    parser.add_argument("--timings", nargs="+", default=TIMINGS, choices=TIMINGS)
    parser.add_argument("--skip-baseline", action="store_true", help="이미 같은 데이터셋으로 S-001 을 돌렸으면 건너뛴다")
    args = parser.parse_args()

    if not args.skip_baseline:
        print("기준 실행")
        if run("S-001") != 0:
            sys.exit("S-001 이 실패해서 멈춘다")

    failed = []
    for point in args.points:
        timings = ["FIRST"] if point in TASKLET_POINTS else args.timings
        for timing in timings:
            print(f"{point} {timing}")
            fault_code = run("S-010", "--point", point, "--timing", timing)
            rerun_code = run("S-010R")
            if fault_code != 0 or rerun_code != 0:
                failed.append(f"{point}·{timing}")

    print("실패한 칸:", ", ".join(failed) if failed else "없음")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
