"""장애를 연속으로 넣는 묶음과 마감된 월 재실행을 차례로 돈다 (TR1L-171).

묶음 하나는 S-010(첫 장애) → S-012(재실행 중 장애, 한 번 이상) → S-012R(마지막 재실행)이다.
실행마다 reliability.run.chain_id 에 묶음 ID 를 남긴다. 묶음은 모든 실행이 PASS 여야 통과다.
마지막에 마감된 월을 같은 cutoff, 같은 월의 다른 cutoff 로 다시 돌린다 (S-013).

python3 infra/reliability/scripts/run_fault_chains.py
python3 infra/reliability/scripts/run_fault_chains.py --chains C1 C5
"""

import argparse
import subprocess
import sys
from datetime import datetime
from pathlib import Path

SCRIPT = Path(__file__).with_name("run_scenario.py")
ROOT = Path(__file__).resolve().parents[2].parent

# (지점, 시점, 종류). 첫 번째가 처음 장애, 나머지는 재실행 중 장애다
# 재실행은 지점을 지나는 횟수가 처음보다 적어서 두 번째부터는 FIRST 를 쓴다
CHAINS = {
    "C1": [("P3c", "MIDDLE", "K1"), ("P3c", "FIRST", "K1")],   # 같은 지점에서 두 번
    "C2": [("P3c", "MIDDLE", "K1"), ("P3b", "FIRST", "K1")],   # 저장 직후 → 저장 전
    "C3": [("P2", "MIDDLE", "K1"), ("P3c", "FIRST", "K1")],    # Step2 → Step3
    "C4": [("P3d", "MIDDLE", "K1"), ("P4", "FIRST", "K1")],    # Step3 → 마감 직전
    "C5": [("P3c", "MIDDLE", "K2"), ("P3a", "FIRST", "K2")],   # 강제 종료 두 번
    "C6": [("P3b", "MIDDLE", "K4"), ("P3d", "FIRST", "K3")],   # MongoDB 끊김 → PostgreSQL 끊김
    "C7": [("P1", "MIDDLE", "K1"), ("P2", "FIRST", "K1"), ("P3c", "FIRST", "K2")],  # 세 번
}
# 마감된 월 재실행. 같은 cutoff 와 같은 월(2026-01 정산)의 다른 cutoff
FINISHED_MONTH_CUTOFFS = ["2026-02-01T00:00:00Z", "2026-02-15T00:00:00Z"]


def run(*args):
    result = subprocess.run(["uv", "run", "-q", str(SCRIPT), *args], cwd=ROOT, capture_output=True, text=True)
    last = (result.stdout.strip().splitlines() or ["(출력 없음)"])[-1]
    print(f"  {' '.join(args):<64} {last}", flush=True)
    return result.returncode


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--chains", nargs="*", help="C1 ~ C7. 비우면 전부")
    parser.add_argument("--skip-finished-month", action="store_true")
    args = parser.parse_args()
    chains = args.chains or list(CHAINS)
    if unknown := set(chains) - set(CHAINS):
        parser.error(f"모르는 묶음 {sorted(unknown)}")

    failed = []
    for name in chains:
        chain_id = f"{name}-{datetime.now():%Y%m%d-%H%M%S}"
        faults = CHAINS[name]
        print(f"{chain_id} " + " → ".join("·".join(f) for f in faults), flush=True)
        codes = []
        for index, (point, timing, kind) in enumerate(faults):
            scenario = "S-010" if index == 0 else "S-012"
            codes.append(run(scenario, "--point", point, "--timing", timing, "--kind", kind, "--chain", chain_id))
        codes.append(run("S-012R", "--chain", chain_id))
        if any(codes):
            failed.append(name)

    if not args.skip_finished_month:
        print("마감된 월 재실행", flush=True)
        for cutoff in FINISHED_MONTH_CUTOFFS:
            if run("S-013", "--cutoff", cutoff) != 0:
                failed.append(f"S-013 {cutoff}")

    print("실패:", ", ".join(failed) if failed else "없음")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
