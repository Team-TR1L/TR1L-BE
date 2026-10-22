# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2"]
# ///
"""실행 한 번의 시간과 자원 사용량을 모은다 (TR1L-174).

- 시간: reliability.run 의 시작·종료, main DB batch 메타 테이블의 step 별 시간
- 자원: Prometheus(worker 18090, 5초 간격)의 JVM heap, 프로세스 CPU, 커넥션 풀 사용 수, GC 시간
결과는 build/reliability-resources/<run_id>.json 에 남기고 표로 출력한다.

uv run infra/reliability/scripts/resource_report.py S-001-20261022-224500 S-010-...
"""

import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

import psycopg

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/reliability-resources"
TARGET_PG = "postgresql://postgres:postgres@localhost:25433/billing_target"
MAIN_PG = "postgresql://postgres:postgres@localhost:25432/billing"
PROMETHEUS = "http://localhost:19091"

QUERIES = {
    "heap_max_mb": ('sum(jvm_memory_used_bytes{area="heap"}) / 1024 / 1024', max),
    "cpu_max_percent": ("process_cpu_usage * 100", max),
    "cpu_avg_percent": ("process_cpu_usage * 100", lambda values: sum(values) / len(values)),
    "target_pool_active_max": ('max(hikaricp_connections_active{pool=~"target.*"})', max),
    "main_pool_active_max": ('max(hikaricp_connections_active{pool!~"target.*"})', max),
}


def prometheus_range(query, start, end):
    params = urllib.parse.urlencode(dict(query=query, start=start.timestamp(), end=end.timestamp(), step="5s"))
    with urllib.request.urlopen(f"{PROMETHEUS}/api/v1/query_range?{params}", timeout=10) as response:
        result = json.load(response)["data"]["result"]
    return [float(value) for series in result for _, value in series["values"]]


def gc_seconds(start, end):
    seconds = int((end - start).total_seconds()) + 5
    params = urllib.parse.urlencode(dict(query=f"sum(increase(jvm_gc_pause_seconds_sum[{seconds}s]))",
                                         time=end.timestamp()))
    with urllib.request.urlopen(f"{PROMETHEUS}/api/v1/query?{params}", timeout=10) as response:
        result = json.load(response)["data"]["result"]
    return round(float(result[0]["value"][1]), 2) if result else None


def step_seconds(start, end):
    """그 구간에 시작한 calculateJob 실행의 step 별 시간. Step3 는 partition 을 뺀 manager step 만 본다"""
    with psycopg.connect(MAIN_PG) as conn:
        rows = conn.execute(
            """SELECT s.step_name, extract(epoch FROM s.end_time - s.start_time), s.exit_code
               FROM batch_step_execution s
               JOIN batch_job_execution e USING (job_execution_id)
               JOIN batch_job_instance i USING (job_instance_id)
               WHERE i.job_name = 'calculateJob' AND e.create_time BETWEEN %s AND %s
                 AND s.step_name NOT LIKE '%%:partition-%%'
               ORDER BY s.step_execution_id""", (start.replace(tzinfo=None), end.replace(tzinfo=None))).fetchall()
    return [dict(step=name, seconds=round(float(seconds), 1) if seconds is not None else None, exit=exit_code)
            for name, seconds, exit_code in rows]


def report(run_id):
    with psycopg.connect(TARGET_PG) as conn:
        row = conn.execute("""SELECT scenario_id, dataset_id, fault_point, fault_timing, fault_kind, started_at,
                                     finished_at, verdict FROM reliability.run WHERE run_id = %s""", (run_id,)).fetchone()
    if not row:
        sys.exit(f"{run_id} 가 없다")
    scenario, dataset, point, timing, kind, started, finished, verdict = row
    resources = {}
    for name, (query, reduce) in QUERIES.items():
        values = prometheus_range(query, started, finished)
        resources[name] = round(reduce(values), 1) if values else None
    result = dict(run_id=run_id, scenario=scenario, dataset=dataset,
                  fault="·".join(x for x in (point, timing, kind) if x) or None, verdict=verdict,
                  seconds=round((finished - started).total_seconds()), resources=resources,
                  gc_seconds=gc_seconds(started, finished), steps=step_seconds(started, finished))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{run_id}.json").write_text(json.dumps(result, ensure_ascii=False, indent=2, default=str))
    return result


def main():
    rows = [report(run_id) for run_id in sys.argv[1:]]
    print("| 실행 | 데이터셋 | 장애 | 판정 | 전체(초) | heap 최대(MB) | CPU 평균/최대(%) | target 풀 최대 | GC(초) | step(초) |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for r in rows:
        res = r["resources"]
        steps = ", ".join(f"{s['step'].replace('Step', '')} {s['seconds']}" for s in r["steps"])
        print(f"| {r['run_id']} | {r['dataset']} | {r['fault'] or '-'} | {r['verdict']} | {r['seconds']} | "
              f"{res['heap_max_mb']} | {res['cpu_avg_percent']}/{res['cpu_max_percent']} | "
              f"{res['target_pool_active_max']} | {r['gc_seconds']} | {steps} |")


if __name__ == "__main__":
    main()
