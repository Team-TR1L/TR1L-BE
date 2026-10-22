# Job1 reliability 로컬 환경

Job1 재실행 검증 시나리오를 로컬에서 돌리고, 장애를 넣은 순간부터 재실행으로 돌아오기까지를 Grafana로 본다.

## 구성

다른 로컬 스택과 포트가 겹치지 않게 compose 프로젝트 이름을 `tr1l-rel`로 두고 포트를 옮겼다.

| 서비스 | 포트 | 용도 |
| --- | --- | --- |
| main DB | 25432 | 사용자, 요금제, 사용량 원천 |
| target DB | 25433 | 청구 대상, `billing_work`, `reliability` 스키마 |
| MongoDB | 27027 | `billing_snapshot` |
| Prometheus | 19091 | 호스트에서 띄운 worker(18090) 지표 |
| Grafana | 13000 | `Job1 Reliability` 대시보드. 익명 Viewer로 볼 수 있음 |
| Toxiproxy | 18474 (API), 25443, 27037 | worker 가 target DB, MongoDB 에 붙는 길. K3, K4 에서 끊는다 |

worker는 컨테이너가 아니라 호스트에서 `java -jar`로 띄운다. 프로세스 강제 종료 같은 장애를 넣기 쉽게 하려는 것이다.

## 처음 한 번

JDK 17과 [uv](https://docs.astral.sh/uv/)가 필요하다.

```bash
./gradlew :apps:worker:bootJar -x test
docker compose -f infra/reliability/docker-compose.reliability.yml up -d --wait
infra/reliability/scripts/setup_local.sh
```

`setup_local.sh`는 main DB에 스키마, 마스터 데이터, 데이터셋 시드를 넣고 target DB에 `billing_work`와 `reliability` 스키마를 만든다.
main DB 테이블은 지우고 다시 만들기 때문에 다시 실행해도 된다.

마지막에 `Job1DatasetDistributionValidationTest`로 적재한 분포가 `dataset.yml`과 같은지 확인한다.
결과는 `apps/worker/build/job1-reliability-results/dataset/<데이터셋>-distribution.json`에 남는다.

## 데이터셋

```bash
infra/reliability/scripts/setup_local.sh                     # D1
infra/reliability/scripts/setup_local.sh D2-boundary
infra/reliability/scripts/setup_local.sh D3-pairwise
infra/reliability/scripts/setup_local.sh D4-random 20261022  # seed 를 바꿔 가며
```

| ID | 사용자 | 만드는 방법 | 적재 뒤 검증 |
| --- | --- | --- | --- |
| D1-rerun-mvp | 30,000 | 고정 시드 | `distribution` 21개 항목 건수가 정확히 같아야 함 |
| D2-boundary | 330 | 경계값 33가지 × 10명 | S-001 뒤 `boundaryCases` 경우별 결과 (`Job1BoundaryCaseValidationTest`) |
| D3-pairwise | 30,000 | 조합 생성기, seed 3 | `dimensionRatios` ±2%, pairwise 100% |
| D4-random | 30,000 | 조합 생성기, seed 지정 | `dimensionRatios` ±2%, pairwise 100% |

조합 생성기(`datasets/_generator/combination_seed.sql`)는 차원마다 `hashtextextended(seq, seed)`로 값을 따로 뽑는다.
그래서 같은 seed면 같은 데이터가 나오고, 할인 조건끼리 겹치지 않는다. user_id의 30%는 5,000,000 뒤로 띄워 빈 구간을 만든다.

입력 차원은 `datasets/_dimensions/job1-input-dimensions.yml`에 있다. 사용량, 약정, 군인, 복지, 옵션 다섯 차원이고,
pairwise 커버리지는 차원 두 개씩 묶었을 때 나올 수 있는 값 쌍 142개 중 실제로 있는 쌍의 비율이다. 모든 데이터셋에서 계산해 결과 파일에 남긴다.

`run_scenario.py`는 `setup_local.sh`가 남긴 `build/reliability-dataset`을 읽어 지금 적재된 데이터셋을 JUnit에 넘기고 실행 기록에 남긴다.

## 시나리오 실행

```bash
uv run infra/reliability/scripts/run_scenario.py S-001    # 정상 실행
uv run infra/reliability/scripts/run_scenario.py S-001R   # 같은 cutoff 재실행 (Step0 NOOP)
uv run infra/reliability/scripts/run_scenario.py S-003    # Mongo 저장 직후 장애
uv run infra/reliability/scripts/run_scenario.py S-003R   # 장애 뒤 재실행
```

S-001R과 S-003R은 S-001이 남긴 `state-snapshot.json`과 비교하므로 S-001을 먼저 돌린다.

시나리오 하나는 다음 순서로 돈다.

1. S-001, S-003이면 2026-01 정산 결과를 비운다
2. `reliability.run`에 실행을 남기고 0.25초마다 상태를 `reliability.state_sample`에 센다
3. worker로 `calculateJob`을 실행한다. S-003은 `APP_BILLING_RELIABILITY_FAIL_AFTER_SNAPSHOT_SAVE=true`
4. 시나리오에 맞는 JUnit 메서드 하나를 돌리고, 결과 JSON을 읽어 `reliability.check_result`와 `reliability.fault_cell`에 남긴다

## 장애 행렬

worker에는 지점 8개에 장애를 넣을 수 있다(`Job1FaultPoint`, `Job1FaultInjector`). 설정이 없으면 예외를 던지지 않는다.

| 지점 | 위치 |
| --- | --- |
| P0 | Step0 billing_cycle 확인 직후 |
| P1 | Step1 billing_targets chunk 적재 직후 |
| P2 | Step2 billing_work chunk 적재 직후 |
| P3a | Step3 claim 직후, 계산 전 |
| P3b | Step3 Mongo 저장 전 |
| P3c | Step3 Mongo 저장 직후, 상태 갱신 전 |
| P3d | Step3 상태 갱신 직후, chunk commit 전 |
| P4 | Step4 billing_cycle 마감 전 |

| 설정 | 의미 |
| --- | --- |
| `app.reliability.fault.point` | 주입 지점. 비어 있으면 주입하지 않는다 |
| `app.reliability.fault.nth` | 그 지점을 몇 번째로 지날 때 주입할지 |
| `app.reliability.fault.repeat` | `ONCE`(기본) 또는 `FROM_NTH` |
| `app.reliability.fault.kind` | `EXCEPTION`(기본), `HALT`(즉시 종료), `PAUSE`(멈추고 재개 파일을 기다림) |
| `app.reliability.fault.pause-dir` | `PAUSE` 표시 파일과 재개 파일 위치 |
| `app.reliability.fault.hits-file` | 종료할 때 지점별 통과 횟수를 남길 파일 |

| 종류 | 장애 | worker 설정 | 재실행 |
| --- | --- | --- | --- |
| K1 | 애플리케이션 예외 | `kind=EXCEPTION` | 바로 |
| K2 | 프로세스 즉시 종료 (`Runtime.halt(137)`, shutdown hook 도 돌지 않음) | `kind=HALT` | lease 만료 뒤 |
| K3 | target DB 연결 끊김 | `kind=PAUSE`, 멈춘 사이 하네스가 `target-pg` proxy 를 끊음 | lease 만료 뒤 |
| K4 | MongoDB 연결 끊김 | `kind=PAUSE`, 멈춘 사이 하네스가 `mongo` proxy 를 끊음 | lease 만료 뒤 |
| K5 | worker 두 개 동시 실행 | 준비 실행 뒤 A, B 를 P2 에서 `kind=PAUSE` 로 붙잡았다가 시점에 맞춰 재개 | 없음. 두 worker 가 끝나면 바로 수렴을 본다 |

K2~K4 는 Step3 반환 listener 가 돌지 못할 수 있어 장애 실행에 lease 를 20초로 주고, 재실행은 남은 PROCESSING 의 lease 가 끝난 뒤에 한다.
P4 × K4 는 Step4 가 MongoDB 를 쓰지 않아 제외 칸이다. K4 × P0~P2 는 Step3 첫 저장에서 실패하므로 장애 직후 예상 상태를 따로 둔다(`expectByKind`).

```bash
python3 infra/reliability/scripts/run_fault_matrix.py                       # K1 20칸 전부
python3 infra/reliability/scripts/run_fault_matrix.py --kinds K2 K3 K4 --skip-baseline   # 59칸
python3 infra/reliability/scripts/run_fault_matrix.py --kinds K5 --skip-baseline          # 3칸
python3 infra/reliability/scripts/run_fault_matrix.py --points P2 --timings MIDDLE
uv run infra/reliability/scripts/run_scenario.py S-010 --point P3a --timing LAST   # 한 칸만
uv run infra/reliability/scripts/run_scenario.py S-010R
```

- S-001이 지점별 통과 횟수를 `build/reliability-fault-hits-<데이터셋>.json`에 남긴다. S-010은 이 값으로 FIRST는 1번째, MIDDLE은 절반, LAST는 90% 지점 호출로 바꾼다. 여러 스레드로 도는 Step은 실행마다 호출 수가 조금씩 달라 마지막 호출을 정확히 맞출 수 없어서다
- S-010은 worker 로그에서 `Injected Job1 fault at <지점>`을 찾는다. 없으면 장애가 들어가지 않은 것이라 실패로 본다
- 장애 직후 예상 상태는 `faults/job1-fault-points.yml`에 있다
- 칸은 S-010과 S-010R이 모두 통과해야 PASS다. S-010R은 S-003R과 같은 수렴 검증(INV-001~004, S-001 비교, C5, C6)을 한다
- 기존 `APP_BILLING_RELIABILITY_FAIL_AFTER_SNAPSHOT_SAVE`(S-003)는 그대로 둔다. 모든 partition의 첫 저장 뒤에 던지는 방식이라 P3c·FIRST의 ONCE와는 다르다

## lease 와 worker 경합

Step3 는 work 를 claim 할 때 `PROCESSING` 과 `lease_until` 을 같이 쓴다. 다른 실행은 lease 가 끝난 `PROCESSING` 만 다시 가져간다.
K1 처럼 예외로 끝나면 반환 listener 가 `TARGET` 으로 돌려놓아서 이 경로가 실행되지 않는다. 그래서 아래 두 시나리오를 따로 둔다.

| 시나리오 | 하는 일 | 통과 조건 |
| --- | --- | --- |
| S-002 | P3a LAST 에서 `Runtime.halt`. 장애 실행의 lease 는 180초 | 커밋된 `PROCESSING` 이 남고 모두 lease 안쪽. 목록을 `processing-left.json` 에 남긴다 |
| S-002E | lease 가 끝나기 전에 같은 cutoff 로 재실행 | 남은 `PROCESSING` 의 상태, `claimed_by`, `attempt_count`, `lease_until` 이 그대로. 나머지는 모두 `CALCULATED`. `billing_cycle` 은 `RUNNING` (`LEASE_HOLD`) |
| S-002R | lease 가 끝난 뒤 재실행 | 남은 work 는 다른 worker 가 한 번만 더 claim(`attempt_count` +1). 나머지는 1. S-001 과 상태, C5, C6 이 같고 `FINISHED` (`LEASE_RECLAIM`) |
| S-011 | K5 한 칸. 아래 순서로 두 worker 가 Step3 claim 을 나눠 가진다 | 모든 work 의 `attempt_count` 가 1. S-001 과 상태, C5, C6 이 같고 `FINISHED` (`CLAIM_ONCE`) |

```bash
uv run infra/reliability/scripts/run_scenario.py S-002     # 기본 시점 LAST
uv run infra/reliability/scripts/run_scenario.py S-002E    # 바로 이어서. 끝났을 때 lease 가 남아 있어야 한다
uv run infra/reliability/scripts/run_scenario.py S-002R    # lease 가 끝날 때까지 기다렸다가 돈다
uv run infra/reliability/scripts/run_scenario.py S-011 --timing MIDDLE
```

- S-002 는 P3a 의 13번째 claim(D1 기준, 첫 partition 이 두 번째 claim 을 하는 순간)에서 끊는다. 첫 claim 들(FIRST, MIDDLE)에서 끊으면 커밋된 claim 이 없어 `PROCESSING` 이 남지 않고, LAST 는 실행마다 남기도 하고 안 남기도 했다
- S-002E 는 worker 가 끝난 순간 남은 lease 를 `LEASE_REMAINING` 사건으로 남긴다. 0 이하면 만료 전 재실행을 본 것이 아니므로 실패로 둔다
S-011 은 이렇게 돈다.

1. 준비 실행. Step3 의 모든 claim 에서 예외를 던져 Step0 ~ Step2 결과만 남긴다 (work 28,500 모두 `TARGET`, `attempt_count` 0)
2. A 를 띄우고 3초 뒤 B 를 18091 포트로 띄운다. 같은 순간에 띄우면 batch 메타 테이블의 SERIALIZABLE 트랜잭션끼리 부딪혀 한쪽이 시작도 못 한다
3. 둘 다 Step2 마지막 쓰기 직후(P2)에서 멈춘다. 이미 work 가 있어서 Step2 는 아무 행도 넣지 않으므로 멈춰 있어도 잠그는 행이 없다
4. A 를 재개하고, CALCULATED 가 FIRST 0%, MIDDLE 50%, LAST 90% 가 되면 B 를 재개한다. lease 는 20초

이렇게 붙잡는 이유는 그냥 같이 띄우면 경합이 일어나지 않아서다. 먼저 Step3 에 들어간 worker 가 claim 을 쥐고 있으면
다른 worker 의 Step2 `INSERT ... ON CONFLICT DO NOTHING` 이 그 행에서 기다리고, Step3 에 왔을 때는 할 일이 거의 없다.
FIRST 칸은 두 worker 가 모두 계산하지 않으면 전제가 깨진 것으로 보고 실패로 둔다. MIDDLE, LAST 는 A 가 partition 마다
2,000건씩 먼저 claim 해 두어서 늦게 들어온 B 가 할 일이 없을 수 있다. 몇 건씩 했는지만 `WORKER_SHARE` 사건으로 남긴다

- `claimed_by` 는 `<pid>@<host>-<partition>` 이라 프로세스별로 몇 건을 계산했는지 `lease-check.json` 에 남는다
- 결과는 `check_result` 의 `PROCESSING_LEFT`, `LEASE_HOLD`, `LEASE_RECLAIM`, `CLAIM_ONCE` 로 남는다

## 연속 장애와 마감된 월 재실행

한 번 끊고 한 번 재실행하는 것만으로는 재실행 도중에 또 끊기는 경우를 보지 못한다. 그래서 장애를 이어서 넣는 묶음을 둔다.

| 시나리오 | 하는 일 | 통과 조건 |
| --- | --- | --- |
| S-012 | 앞 장애 뒤 재실행 도중 다시 장애. `--point`, `--timing`, `--kind` 로 고른다 | 앞 실행이 끝낸 CALCULATED 를 잃지 않음, 있는 snapshot 이 모두 S-001 과 같음, 끝나지 않은 월을 마감하지 않음 (`PROGRESS_KEPT`) |
| S-012R | 장애를 여러 번 겪은 뒤 마지막 재실행 | S-010R 과 같은 수렴 검증 (INV-001~004, S-001 비교, C5, C6), `FINISHED` |
| S-013 | 마감된 월을 다시 실행. `--cutoff` 로 같은 월의 다른 cutoff 를 줄 수 있다 | Step0 만 돌고 NOOP, billing_work·billing_targets·billing_cycle 행 전체와 snapshot 문서 전체(수정 시각 포함)가 그대로 (`MONTH_UNCHANGED`) |

```bash
python3 infra/reliability/scripts/run_fault_chains.py              # C1 ~ C7, 이어서 S-013 두 번
python3 infra/reliability/scripts/run_fault_chains.py --chains C5 --skip-finished-month
uv run infra/reliability/scripts/run_scenario.py S-013 --cutoff 2026-02-15T00:00:00Z
```

| 묶음 | 장애 순서 |
| --- | --- |
| C1 | P3c·MIDDLE·K1 → P3c·FIRST·K1 |
| C2 | P3c·MIDDLE·K1 → P3b·FIRST·K1 |
| C3 | P2·MIDDLE·K1 → P3c·FIRST·K1 |
| C4 | P3d·MIDDLE·K1 → P4·FIRST·K1 |
| C5 | P3c·MIDDLE·K2 → P3a·FIRST·K2 |
| C6 | P3b·MIDDLE·K4 → P3d·FIRST·K3 |
| C7 | P1·MIDDLE·K1 → P2·FIRST·K1 → P3c·FIRST·K2 |

- 실행마다 `reliability.run.chain_id` 에 묶음 ID 가 남고, 대시보드 "연속 장애와 마감된 월 재실행" 표가 묶음 단위로 보여 준다
- 재실행은 지점을 지나는 횟수가 처음보다 적어서 두 번째 장애부터는 FIRST 를 쓴다
- 앞 실행이 남긴 PROCESSING 이 있으면 재실행 전에 lease 가 끝나기를 기다린다. S-002E 만 기다리지 않는다
- S-013 은 worker 를 띄우기 전에 `captureFinishedMonth` 로 상태를 남기고, 돌린 뒤 batch 메타 테이블(main DB)에서 마지막 calculateJob 의 step 이 `billingGateStep=NOOP` 하나뿐인지 본다

## 실행 사이 원천 변경

장애와 재실행 사이에 main DB 원천이 바뀌는 경우를 본다. Step1 은 cutoff 시각을 쓰지 않고 정산 월(시작일, 종료일)로만 읽는다.
사용자 상태와 요금제는 지금 값을 읽고, 재실행하면 `billing_targets` 를 모든 사용자에 대해 다시 덮어쓴다(`ON CONFLICT DO UPDATE`).

| ID | 바꾸는 것 | 고르는 사용자 |
| --- | --- | --- |
| I1 | cutoff 뒤 사용량 재집계. 2026-01 사용량 +5,000MB, `usage_aggregated_at` 을 지금으로 | 초과 요금이 붙는 DBT-02 |
| I2 | 탈퇴. `user_status = WITHDRAWN` | 청구 대상 |
| I3 | 옵션 해지. 구독 `end_date` 를 지금으로 | 옵션이 있는 사용자 |

```bash
python3 infra/reliability/scripts/run_input_changes.py                 # P3c·MIDDLE, P1·MIDDLE × I1 ~ I3
python3 infra/reliability/scripts/run_input_changes.py --changes I2 --points P1
```

- 칸 하나는 S-010(장애) → S-014R --change(원천을 바꾸고 재실행)이다. 원천은 하네스가 끝날 때 되돌린다(`atexit`)
- 바뀐 사용자는 장애 직후 상태로 세 무리로 나눠 30명씩 고른다. `CALCULATED`(계산 끝남), `LOADED`(billing_targets 에만 있음), `NOT_LOADED`(Step1 이 아직 넣지 않음)
- 기준으로 삼은 규칙은 "cutoff 기준 유지", 곧 바뀐 사용자도 S-001 과 같은 청구서를 받는 것이다. 아직 요구사항으로 정해지지 않아서 어긴 칸은 `DECISION` 으로 남긴다. 구조(invariant, 남은 work 없음, 마감)나 바뀌지 않은 사용자가 S-001 과 다르면 `FAIL` 이다
- 결과는 `check_result` 의 `CUTOFF_HOLD` 와 대시보드 "실행 사이 원천 변경" 표에 남는다

| 칸 | 결과 |
| --- | --- |
| I1 · Step1 뒤 장애 | 계산 끝난 30명은 예전 사용량, 아직 계산 안 한 30명은 새 사용량으로 청구. 계산 끝난 30명은 청구서와 billing_targets 가 어긋남 → DECISION |
| I1 · Step1 도중 장애 | 60명 모두 새 사용량으로 청구 → DECISION |
| I2 · Step1 뒤 장애 | 모두 S-001 과 같음 (이미 billing_targets 에 들어가 있어서) → PASS |
| I2 · Step1 도중 장애 | Step1 이 아직 넣지 않은 30명은 cutoff 때 청구 대상이었는데 청구되지 않음 → DECISION |
| I3 · 두 칸 | 모두 S-001 과 같음 (해지일이 1월 뒤라 1월 구독은 그대로) → PASS |

## 무작위 반복 실행

정해 둔 칸만 돌리면 생각하지 못한 조합은 빠진다. `run_random.py` 는 D4 seed 와 장애를 무작위로 골라 N 번 돈다.

- 한 번은 장애(S-010) → 30% 확률로 재실행 중 장애 한 번 더(S-012) → 마지막 재실행(S-010R 또는 S-012R)이다
- 지점 8개, 시점 3개, 종류 K1~K4 에서 고른다. P0·P4 는 시점이 FIRST 뿐이고, P4 × K4 는 뺀다. 두 번째 장애는 FIRST 로 둔다
- D4 seed 하나마다 데이터셋을 다시 적재하고 S-001 기준을 만든다. 끝나면 D1 으로 되돌리고 S-001 을 다시 돈다(`--keep-dataset` 으로 끌 수 있다)
- 모든 선택은 `--seed` 하나에서 나온다. 같은 `--seed` 면 같은 순서로 돌고, `--only <번호>` 로 한 번만 다시 돌 수 있다
- 장애가 들어가지 않았거나 전제가 깨진 번은 `INVALID` 로 두고 N 에서 뺀다. 시스템이 틀린 것이 아니라 그 번을 검증하지 못한 것이라서다

```bash
uv run infra/reliability/scripts/run_random.py --seed 20261022 --datasets 3 --per-dataset 10    # N=30
uv run infra/reliability/scripts/run_random.py --seed 20261022 --datasets 3 --per-dataset 10 --only 17
```

- 한 번에 평균 100초쯤, D4 적재와 S-001 에 70초쯤 걸린다. N=30 은 1시간, N=100 은 3시간쯤이다
- 결과는 `build/reliability-random/random-<seed>.json`, `.md` 에 남는다. 실패한 번에는 그대로 다시 돌리는 명령(`setup_local.sh D4-random <seed>` 부터)을 적는다
- 한 번마다 `scenario_id = RANDOM` 인 요약 행이 남고, 대시보드 "재실행 실패율 95% 상한" 과 "무작위 반복 실행" 표가 이 행을 읽는다
- 실패가 없으면 상한은 rule of three 로 3/N 이다. N=30 이면 10%, N=100 이면 3%, N=300 이면 1%

## 108만 명 규모 (D5)

chunk 수, partition 크기, lease 시간이 커지면 작은 데이터셋에서 보이지 않던 결과가 나올 수 있다.
D5-scale 은 D3 와 같은 조합 생성기, 같은 seed 3 으로 사용자만 1,080,000명으로 늘린 데이터셋이다. 차원별 비율은 D3 와 같다.

| | D3-pairwise | D5-scale |
| --- | --- | --- |
| 사용자 | 30,000 | 1,080,000 |
| 청구 대상 | 25,539 | 918,473 |
| 옵션 구독 | | 1,782,468 |
| Step3 claim 횟수(S-001) | | 460 |

```bash
infra/reliability/scripts/setup_local.sh D5-scale                       # main DB 적재 2분 반쯤
REL_SAMPLE_INTERVAL=2 uv run infra/reliability/scripts/run_scale.py     # S-001 과 핵심 칸 4개
uv run infra/reliability/scripts/run_scale.py --compare D3-pairwise D5-scale
uv run infra/reliability/scripts/resource_report.py <run_id> ...        # 한 실행의 시간과 자원 사용량
```

- 핵심 칸은 P3a·MIDDLE·K1, P3c·MIDDLE·K1, P3a·MIDDLE·K2, K5·FIRST 다. 같은 칸을 D3 에서도 돌려 비교한다
- 검증 JUnit 은 사용자 전원의 분포, snapshot 해시, 금액을 메모리에 올린다. D5 에서는 기본 512MB 로 `OutOfMemoryError` 가 나서 테스트 heap 을 4GB 로 올렸다
- 상태 표본은 한 번 세는 데 시간이 걸려 `REL_SAMPLE_INTERVAL=2` 로 2초마다 센다
- `resource_report.py` 는 실행 구간의 Prometheus 지표(JVM heap, 프로세스 CPU, 커넥션 풀, GC)와 batch 메타 테이블의 step 별 시간을 `build/reliability-resources/<run_id>.json` 에 남긴다
- `run_scale.py` 결과는 `build/reliability-scale/<데이터셋>.json` 이다
- MongoDB WiredTiger 캐시를 1.5GB 로 묶었다(compose `--wiredTigerCacheSizeGB`). 기본값으로는 D5 재실행 중 Docker VM(8GB) 메모리가 모자라 mongo 가 OOM 으로 죽었다
- K5 는 worker 가 Step2 끝에서 2분 넘게 멈춰 있으면 실패하게 되어 있다. D5 처럼 Step1 이 5분쯤 걸리면 90초 안에 둘 다 멈추지 않으므로 붙잡지 않고 그대로 진행한다(`WORKER_GATE_SKIPPED`). FIRST 칸은 두 worker 가 모두 계산했는지로 경합을 확인한다

| 칸 | D3 | D5 | D5 장애 실행 + 재실행 시간 | D5 heap 최대 |
| --- | --- | --- | --- | --- |
| S-001 | PASS 99초 | PASS 688초 (Step1 354, Step3 185) | | 723MB |
| P3a·MIDDLE·K1 | PASS | PASS | 570 + 528초 | 1,587MB |
| P3c·MIDDLE·K1 | PASS | PASS | 549 + 839초 | 1,316MB |
| P3a·MIDDLE·K2 | PASS | PASS | 477 + 558초 | 1,404MB |
| K5·FIRST | PASS | **FAIL** C5 1명 다름 (918,472 / 918,473), 나머지 검증 통과 | 1,034초 | 639MB |

K5 의 C5 불일치는 #384(TR1L-178) 결함이다. Step1 옵션 조회에 `ORDER BY` 가 없고, D5 에서는 옵션 구독 테이블(130MB)이 `shared_buffers`(128MB)의 1/4 보다 커서
PostgreSQL synchronized seqscan 이 테이블 중간에서 시작할 수 있다. 그 시작 블록에 걸친 사용자는 옵션 순서가 달라지고, 금액은 같지만 snapshot 의 옵션 라인 순서와 라인 ID 가 바뀐다.
D1·D3 는 테이블이 작아서 이 동작이 일어나지 않는다.


## 재실행 안전 장치를 깨뜨려 보기 (mutant)

검증이 전부 통과해도 검증이 약해서 통과했을 수 있다. 그래서 재실행 안전성을 떠받치는 장치를 하나씩 깨뜨린 worker 로
장애 칸을 돌려, 검증이 실패하는지 본다.

mutant 는 원본 클래스를 복사해 한 곳만 바꾼 것이다(`apps/worker/src/reliabilityMutant/<ID>`). mutant 마다 jar 를 따로 만들고,
하네스가 worker 를 `PropertiesLauncher` 로 띄우면서 `loader.path` 에 붙인다. `loader.path` 의 클래스가 jar 안의 같은 이름 클래스보다
먼저 로드돼서 원본을 가린다. 운영 jar(`worker.jar`)에는 들어가지 않는다. mutant 클래스는 로드될 때 `Job1 mutant <ID> active` 를 남기고,
하네스는 이 표시가 없으면 판정하지 않는다.

| ID | 깨뜨리는 것 | 바꾼 클래스 | 돌리는 칸 | 잡아야 할 조건 |
| --- | --- | --- | --- | --- |
| M1 | Step2 `ON CONFLICT DO NOTHING` 제거 | `BillingWorkJdbcAdapter` | P3c·MIDDLE·K1 | C2 |
| M2 | snapshot 을 upsert 대신 insert | `MongoBillingSnapshotSaveAdapter` | P3c·MIDDLE·K1 | C4, C5 |
| M3 | lease 가 끝난 PROCESSING 재선점 제거 | `JdbcWorkDocClaimAdapter` | S-002 → S-002R | C3 |
| M4 | 상태 갱신을 snapshot 저장보다 먼저 | `CalculateAndSnapshotWriter` | P3c·MIDDLE·K4 | C4 |
| M5 | 고정된 cutoff 대신 실행 시각 | `CalculateJobContextInitializer` | P3c·MIDDLE·K1 | C1 |
| M6 | 약정 할인 내림을 올림으로 | `ContractDiscountPolicy` | P3c·MIDDLE·K1 | C6 |

```bash
./gradlew :apps:worker:reliabilityMutantJars
uv run infra/reliability/scripts/run_mutants.py          # M1 ~ M6
uv run infra/reliability/scripts/run_mutants.py M6
uv run infra/reliability/scripts/run_scenario.py S-010 --point P3c --timing MIDDLE --mutant M6   # 한 칸만
```

- 칸의 장애 실행과 재실행 중 검증이 하나라도 실패하면 잡은 것이다. 결과는 `check_result` 의 `M1` ~ `M6` 와 대시보드 "재실행 안전 장치 mutant" 표에 남는다
- mutation score 는 mutant 마다 가장 최근 결과로 낸다
- mutant 실행은 장애 행렬 칸을 갱신하지 않고, 재실행 실패율 집계에서도 빠진다(`run.mutant`)
- M5 는 실행 시각 기준 전월(지금이면 2026-09)을 정산해서, 끝나면 그 월의 결과를 지운다
- M4 는 살아남는다. snapshot 저장과 상태 갱신이 같은 PostgreSQL 트랜잭션 안이고 커밋이 Mongo 저장 뒤라서, 둘의 순서를 바꿔도 결과가 같다. 막고 있는 것은 순서가 아니라 커밋 시점이다

## 계산 코드 mutation (PIT)

청구 금액 계산은 `contexts/billing` 의 `BillingAmountReferenceTest` 가 C6 참조 계산과 항목별로 비교한다.
입력과 기대 금액은 `make_reference_cases.py` 가 참조 SQL 로 만든다. D1 에서 요금 계산에 영향을 주는 값 조합마다 한 명(400건)과
기본 제공량 바로 앞뒤, 복지 할인 상한에 정확히 닿는 경우 같은 경계값 행(9건)이다. 이름과 연락처는 테스트용 값으로 바꾼다.

```bash
uv run infra/reliability/scripts/make_reference_cases.py   # D1 을 적재하고 S-001 을 돌린 뒤
./gradlew :contexts:billing:test :contexts:billing:pitest
```

PIT 대상은 `domain.service`, `domain.policy`, `domain.model`, `application.assembler` 이고, 보고서는 `contexts/billing/build/reports/pitest/index.html` 에 남는다.

## 결과 수집 스키마

target DB의 `reliability` 스키마에만 쓴다. 운영 테이블과 섞이지 않는다.

| 테이블 | 한 행 |
| --- | --- |
| `run` | 실행 한 번. 시나리오, 데이터셋, 장애 지점·시점·종류, commit, 종료 코드, 판정 |
| `run_event` | worker 시작·종료, 장애 관측, 재실행 시작. 대시보드에서 세로선으로 그린다 |
| `state_sample` | 0.25초마다 센 상태별 work 수, snapshot 수, 결과만 있는 work 수 |
| `check_result` | 실행별 invariant, S-001 비교, C5, C6 결과 |
| `fault_cell` | 장애 행렬(지점 8 × 시점 3 × 종류 5) 한 칸의 최신 결과 |

## snapshot 내용 검증

건수만 맞고 금액이 다른 경우를 잡기 위한 두 가지 검증이다.

| 검증 | 시나리오 | 내용 |
| --- | --- | --- |
| C5 snapshot 해시 | S-001R, S-003R | S-001이 남긴 사용자별 해시(`snapshot-hashes.json`)와 지금 해시를 비교. `createdAt`, `issuedAt`, `updatedAt`은 빼고 키를 정렬해 SHA-256을 낸다 |
| C6 참조 금액 | S-001, S-001R, S-003R | `checks/reference/job1-billing-amount.sql`이 `billing_targets`만으로 다시 계산한 금액과 snapshot 금액을 사용자, 항목 단위로 비교 |

C6의 계산 규칙은 운영 계산 코드를 읽고 SQL로 다시 쓴 것이다. 요구사항 문서에 할인 기준 금액과 내림 규칙이 없어서다.
그래서 Java 계산 경로나 저장 단계에서 생긴 차이는 잡지만, 규칙을 처음부터 잘못 해석한 경우는 두 쪽이 같이 틀릴 수 있다.

## 캡처

```bash
infra/reliability/scripts/capture.sh full now-15m now
PANEL=2 SIZE=1600,635 infra/reliability/scripts/capture.sh graph now-5m now
```

`build/reliability-screenshots/`에 2배 해상도 PNG로 저장한다. 로컬 Chrome의 headless 모드를 쓴다.
패널 번호는 2 상태 그래프, 5 결과만 있는 work 추이, 11 장애 행렬, 13 검증 결과다.

## 알아 둘 것

- target DB의 Liquibase는 `dev` context만 적용해 `billing_work`를 만들지 않는다. `setup_local.sh`가 만든다
- batch 메타 테이블은 기본 profile에서 만들어지지 않아 하네스가 `SPRING_BATCH_JDBC_INITIALIZE_SCHEMA=always`를 넘긴다
- `SECRET_KEY`는 Job1에서 쓰지 않는다. 하네스가 실행할 때마다 임의 값을 만든다
- 결과만 있는 work 수는 `snapshot 수 - CALCULATED 수`다. 중복 snapshot이 없다는 전제가 있고, 그 전제는 INV-001과 S-001 비교가 따로 확인한다
