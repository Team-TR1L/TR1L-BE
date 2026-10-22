# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2"]
# ///
"""Step1 옵션 조회가 같은 입력에 늘 같은 순서를 내는지 본다 (TR1L-178).

옵션 조회를 두 세션에서 0.3초 차이로 겹쳐 돌린다. 테이블이 shared_buffers 의 1/4 보다 크면
PostgreSQL synchronized seqscan 때문에 뒤 세션이 앞 세션이 읽고 있는 블록부터 읽기 시작한다.
조회에 순서가 정해져 있지 않으면 그 시작 블록에 걸친 사용자의 옵션 순서가 달라진다.
매번 두 세션 결과와 첫 번째 결과를 사용자 단위로 비교한다. 대상은 지금 main DB 의 청구 대상 전원이다.

uv run infra/reliability/scripts/check_option_order.py                    # 지금 SQL 파일
uv run infra/reliability/scripts/check_option_order.py --ref origin/develop  # 그 커밋의 SQL
"""

import argparse
import collections
import subprocess
import threading
import time
from pathlib import Path

import psycopg

ROOT = Path(__file__).resolve().parents[3]
SQL_PATH = "contexts/billing/src/main/resources/sql/job1/step1/main/05_select_included_options.sql"
MAIN_PG = "postgresql://postgres:postgres@localhost:25432/billing"
PARAMS = dict(start="2026-01-01", end="2026-01-31")


def load_sql(ref):
    text = (subprocess.run(["git", "show", f"{ref}:{SQL_PATH}"], cwd=ROOT, capture_output=True, text=True,
                           check=True).stdout if ref else (ROOT / SQL_PATH).read_text())
    return text.replace(":startDate::date", "%(start)s::date").replace(":endDate::date", "%(end)s::date")


def run(sql, name, out, delay, barrier):
    with psycopg.connect(MAIN_PG) as conn:
        conn.execute("CREATE TEMP TABLE temp_user_ids (user_id bigint PRIMARY KEY)")
        conn.execute("""INSERT INTO temp_user_ids SELECT user_id FROM users
                        WHERE user_role = 'USER' AND user_status = 'ACTIVE' AND plan_code IS NOT NULL""")
        barrier.wait()
        time.sleep(delay)
        per_user = collections.defaultdict(list)
        for user_id, code, *_ in conn.execute(sql, PARAMS):
            per_user[user_id].append(code)
        out[name] = per_user


def differing(a, b):
    return [user for user in a if a[user] != b.get(user)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ref", help="이 git 커밋의 SQL 로 돈다. 없으면 작업 트리의 파일")
    parser.add_argument("--trials", type=int, default=5)
    args = parser.parse_args()
    sql = load_sql(args.ref)

    with psycopg.connect(MAIN_PG) as conn:
        size, buffers = conn.execute("""SELECT pg_relation_size('user_option_subscription'),
                                               pg_size_bytes(current_setting('shared_buffers'))""").fetchone()
    print(f"SQL {args.ref or '작업 트리'}, user_option_subscription {size // 2**20}MB, "
          f"shared_buffers {buffers // 2**20}MB (synchronized seqscan {'켜짐' if size > buffers / 4 else '안 켜짐'})")

    first, total_between, total_across = None, 0, 0
    for trial in range(args.trials):
        out, barrier = {}, threading.Barrier(2)
        threads = [threading.Thread(target=run, args=(sql, name, out, delay, barrier))
                   for name, delay in (("A", 0), ("B", 0.3))]
        [thread.start() for thread in threads]
        [thread.join() for thread in threads]
        first = first or out["A"]
        between = differing(out["A"], out["B"])
        across = sorted(set(differing(first, out["A"])) | set(differing(first, out["B"])))
        total_between += len(between)
        total_across += len(across)
        print(f"  {trial + 1}번: 사용자 {len(out['A'])}, 두 세션 사이 순서가 다른 사용자 {len(between)}, "
              f"첫 결과와 다른 사용자 {len(across)} {across[:3]}")
    print(f"합계: 두 세션 사이 {total_between}, 첫 결과와 {total_across}")
    raise SystemExit(1 if total_between or total_across else 0)


if __name__ == "__main__":
    main()
