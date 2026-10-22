"""장애와 재실행 사이에 원천을 바꾸는 칸을 차례로 돈다 (TR1L-172).

칸 하나는 S-010(장애) → S-014R --change(원천을 바꾸고 재실행, 끝나면 원천을 되돌림)이다.
장애 지점은 Step1 이 끝난 뒤(P3c·MIDDLE)와 Step1 도중(P1·MIDDLE) 두 가지다.

python3 infra/reliability/scripts/run_input_changes.py
python3 infra/reliability/scripts/run_input_changes.py --changes I2 --points P1
"""

import argparse
import subprocess
import sys
from datetime import datetime
from pathlib import Path

SCRIPT = Path(__file__).with_name("run_scenario.py")
ROOT = Path(__file__).resolve().parents[2].parent
POINTS = {"P3c": "Step1 이 끝난 뒤", "P1": "Step1 도중"}
CHANGES = ["I1", "I2", "I3"]


def run(*args):
    result = subprocess.run(["uv", "run", "-q", str(SCRIPT), *args], cwd=ROOT, capture_output=True, text=True)
    last = (result.stdout.strip().splitlines() or ["(출력 없음)"])[-1]
    print(f"  {' '.join(args):<56} {last}", flush=True)
    return last


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--changes", nargs="*", help="I1 ~ I3. 비우면 전부")
    parser.add_argument("--points", nargs="*", help="P3c, P1. 비우면 둘 다")
    args = parser.parse_args()
    changes = args.changes or CHANGES
    points = args.points or list(POINTS)

    verdicts = []
    for point in points:
        for change in changes:
            chain_id = f"{change}-{point}-{datetime.now():%Y%m%d-%H%M%S}"
            print(f"{chain_id} {POINTS[point]} 장애 뒤 {change}", flush=True)
            run("S-010", "--point", point, "--timing", "MIDDLE", "--chain", chain_id)
            last = run("S-014R", "--change", change, "--chain", chain_id)
            verdicts.append((chain_id, last.split("junit=")[-1] if "junit=" in last else "?"))

    print("결과:", ", ".join(f"{chain} {verdict}" for chain, verdict in verdicts))
    sys.exit(1 if any(verdict not in ("PASS", "DECISION") for _, verdict in verdicts) else 0)


if __name__ == "__main__":
    main()
