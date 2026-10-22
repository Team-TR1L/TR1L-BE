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

worker는 컨테이너가 아니라 호스트에서 `java -jar`로 띄운다. 프로세스 강제 종료 같은 장애를 넣기 쉽게 하려는 것이다.

## 처음 한 번

JDK 17과 [uv](https://docs.astral.sh/uv/)가 필요하다.

```bash
./gradlew :apps:worker:bootJar -x test
docker compose -f infra/reliability/docker-compose.reliability.yml up -d --wait
infra/reliability/scripts/setup_local.sh
```

`setup_local.sh`는 main DB에 스키마, 마스터 데이터, D1 시드를 넣고 target DB에 `billing_work`와 `reliability` 스키마를 만든다.
main DB 테이블은 지우고 다시 만들기 때문에 다시 실행해도 된다.

마지막에 `Job1DatasetDistributionValidationTest`로 적재한 분포가 `dataset.yml`의 `distribution`과 같은지 확인한다.
비율 항목(`...Ratio`)은 `targetEligible`을 곱한 건수로 바꿔 비교하고, 하나라도 다르면 실패한다.
결과는 `apps/worker/build/job1-reliability-results/dataset/D1-rerun-mvp-distribution.json`에 남는다.

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
