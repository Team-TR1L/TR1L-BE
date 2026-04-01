---
title: ADR-0009 Delivery 백프레셔 정책 - 고정 스레드풀(180) + 무버퍼(Queue 0) + Block
parent: 당위성/의사결정(Decisions)
nav_order: 18
---

> #### 작성일 : 2026-04-01

---

## 1. Context (상황/배경)

- **어디 이야기인가요? (Scope)**: `delivery-server` 비동기 발송 워커의 executor 정책
- **규모는 어느 정도인가요? (Scale)**:
  - burst 구간에서 Kafka 요청 이벤트 급증
  - 외부 발송은 채널별 I/O 대기(현재 mock 기준 1초 지연) 포함
- **피할 수 없는 조건은? (Constraints)**:
  - 무제한 큐 적체는 지연 폭증과 OOM 위험을 키움
  - 처리량을 높이되 컨슈머가 감당 가능한 범위로 유입을 제어해야 함
- **현재 흐름은? (Current flow)**:
  - `CompletableFuture.runAsync(..., notificationExecutor)`로 발송을 비동기 처리
  - executor가 포화되면 제출 스레드(컨슈머)가 block되어 자연스럽게 백프레셔 발생

---

## 2. Problem (문제)

- **증상(Symptom)**:
  - 스레드 수가 낮으면 결과 처리 지연이 커짐
  - 반대로 무제한 큐/과도한 동시성은 메모리/지연 불안정성을 증가시킴
- **원인(Root cause)**:
  - Delivery는 CPU bound보다는 I/O 대기 비중이 높아 일반적인 CPU 지표만으로 최적값 산정이 어려움
  - burst 시 “받아두기만 하는 큐”가 커지면 tail latency가 급격히 증가
- **리스크(Risk)**:
  - 처리량 미달 시 Lag 누적, SLA 미달
  - 과도한 적체 시 메모리 압박 및 장애 전파

---

## 3. Options (대안)

### Option A — 고정 스레드 + 대기 큐(버퍼) 허용
- **한 줄 요약(Summary)**: 작업을 큐에 쌓아두고 워커가 순차 소화
- **좋은 점(Pros)**:
  - 컨슈머 스레드가 쉽게 block되지 않음
  - 단기 burst 흡수에 유리
- **아쉬운 점(Cons)**:
  - 큐 적체가 커질수록 tail latency 증가
  - 큐 길이 관리 실패 시 메모리 위험 증가

### Option B — 고정 스레드(180) + Queue 0 + Block (선택)
- **한 줄 요약(Summary)**: 큐를 두지 않고 실행 슬롯이 없으면 제출자가 대기하도록 하여 백프레셔를 즉시 전파
- **좋은 점(Pros)**:
  - backlog를 메모리가 아니라 Kafka lag/입력 속도로 노출
  - 시스템이 감당 불가능한 속도로 작업을 쌓지 않음
  - 성능 실험에서 180 스레드 구간이 로컬 환경 기준 최적점에 가까웠음
- **아쉬운 점(Cons)**:
  - 포화 시 컨슈머 스레드 block으로 소비 지연이 즉시 나타남
  - 스레드 수가 환경/리소스 변화에 민감

### Option C — 동적 스레드풀(autosizing) + 적응형 큐
- **한 줄 요약(Summary)**: 런타임 지표에 따라 스레드/큐를 동적으로 조절
- **좋은 점(Pros)**:
  - 환경별 자동 최적화 잠재력
  - 피크/비피크 자동 대응 가능
- **아쉬운 점(Cons)**:
  - 제어 로직과 튜닝 복잡도 증가
  - 예측 가능성과 장애 분석 난이도 상승

#### Quick Compare (간단 비교)

| Option | 성능(Performance) | 안정성(Reliability) | 운영성(Operability) | 비용(Cost) | 개발(DevEx) | 결론(Verdict) |
|--------|----------------:|-----------------:|-----------------:|---------:|----------:|-------------|
| A | 중 | 중 | 중 | 중 | 상 | 미채택 |
| B | 상 | 상 | 상 | 상 | 상 | 채택 |
| C | 중 | 중 | 하 | 중 | 하 | 보류 |

---

## 4. Decision (최종 선택)

- **우리는 이것을 선택했다(Decision)**: **Option B — 고정 스레드(180) + Queue 0 + Block**
- **한 줄 이유(One-liner)**: 큐 적체를 숨기지 않고 입력단으로 즉시 백프레셔를 전파해, Delivery 처리량과 안정성을 균형 있게 유지할 수 있어서.

---

## 5. Consequences (결과/영향)

### ✅ 좋아진 점(Pros)
- 무제한 작업 적체를 방지하고 지연 폭증 위험을 줄임
- 포화 시점이 명확해 모니터링/스케일링 판단이 단순해짐
- 로컬 1vCPU/2GB 실험에서 180 스레드 구간이 대표 최적값으로 확인됨

### ⚠️ 감수한 점(Cons)
- 고정값(180)은 환경 종속적이며 인프라 스펙 변경 시 재튜닝 필요
- block 정책으로 인해 피크 구간에 즉시 지연이 관측될 수 있음

### 🔧 운영 관점(Ops notes)
- 주기적으로 다음 지표를 함께 모니터링:
  - Kafka lag
  - 컨슈머 처리량(msg/s, msg/min)
  - executor 포화 빈도/지속시간
  - `SENT` 체류량

---

## 6. Evidence (증빙)

### Code references
- executor 설정:
  - `corePoolSize=180`, `maxPoolSize=180`, `queueCapacity=0`
  - 포화 시 `RejectedExecutionHandler`에서 `queue.put`으로 block
  - 파일: `contexts/dispatch/src/main/java/com/tr1l/delivery/infra/config/AsyncConfig.java`
- 비동기 작업 제출:
  - 파일: `contexts/dispatch/src/main/java/com/tr1l/delivery/application/service/DeliveryWorker.java`

### Measurements
- 로컬 실험 요약:
  - 스레드 100~150: 1만 건 1분45초
  - 스레드 150~170: 1만 건 1분30초
  - 스레드 180: 1만 건 1분15초
  - 스레드 200: 1만 건 1분30초 (개선 정체)
- 근거 문서: `docs/50_performance/delivery/perf-delivery-0001.md`

