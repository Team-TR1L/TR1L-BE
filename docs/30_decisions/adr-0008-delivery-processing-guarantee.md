---
title: ADR-0008 Delivery 처리 보장 모델 - At-least-once + 상태전이 멱등
parent: 당위성/의사결정(Decisions)
nav_order: 17
---

> #### 작성일 : 2026-04-01

---

## 1. Context (상황/배경)

- **어디 이야기인가요? (Scope)**: `delivery-server`의 Kafka 소비/오프셋 커밋/발송 처리 보장 모델
- **규모는 어느 정도인가요? (Scale)**:
  - 월 100만 건 이상 발송 대상 처리
  - burst 구간에서 `dispatch-events-v1` 초당 최대 1.88K 유입
- **피할 수 없는 조건은? (Constraints)**:
  - 외부 I/O(S3 다운로드, 채널 발송) 실패 가능성 상시 존재
  - 중복 발송은 방지해야 함
  - 재시도/재처리를 고려한 운영 모델 필요
- **현재 흐름은? (Current flow)**:
  - 요청 이벤트를 소비하면 먼저 DB 상태를 `READY/FAILED -> SENT`로 전이 시도
  - 이후 비동기 워커에서 복호화/S3 조회/채널 발송을 수행하고 결과 이벤트를 발행
  - 결과 이벤트를 다시 소비해 `SENT -> SUCCEED/FAILED`를 확정

---

## 2. Problem (문제)

- **증상(Symptom)**:
  - Kafka ack는 비동기 발송 완료 전에 수행된다.
  - 발송/결과 발행 단계 실패 시 오프셋은 이미 커밋된 상태가 될 수 있다.
- **원인(Root cause)**:
  - 소비 스레드와 실제 발송 스레드가 분리되어 있고, ack 시점이 발송 완료와 분리됨
  - 외부 I/O 성공 여부를 Kafka 트랜잭션 단위로 묶지 않음
- **리스크(Risk)**:
  - 전달 보장을 메시징만으로 달성할 수 없음
  - 대신 상태 전이 모델이 불완전하면 중복 발송 또는 누락 추적 불가 상태가 발생 가능

---

## 3. Options (대안)

### Option A — Kafka exactly-once에 최대한 의존
- **한 줄 요약(Summary)**: 트랜잭션 기반 처리 보장으로 메시징 계층에서 정합성을 해결
- **좋은 점(Pros)**:
  - 메시지 중복/재처리 부담 감소
  - 개념적으로 단순한 “메시징 중심 보장” 모델
- **아쉬운 점(Cons)**:
  - 외부 I/O(S3, 발송 API) 성공까지 Kafka 트랜잭션으로 원자화할 수 없음
  - 실제 문제(외부 의존 실패)는 여전히 애플리케이션 상태 모델로 다뤄야 함

### Option B — At-least-once + 상태전이 멱등 (선택)
- **한 줄 요약(Summary)**: 메시지는 최소 1회 전달로 보고, 중복/재처리 안전성은 상태 전이 SQL로 보장
- **좋은 점(Pros)**:
  - 외부 I/O 실패를 현실적으로 수용하면서도 중복 발송 방어 가능
  - 재처리 기준을 `send_status`/`attempt_count`로 일관되게 유지 가능
  - 현재 코드 구조와 일치해 구현/운영 복잡도 증가가 작음
- **아쉬운 점(Cons)**:
  - ack 이후 비동기 실패 시 즉시 재소비가 아닌 상태 기반 재대상 선별에 의존
  - 상태 모델과 운영 모니터링 품질이 보장 모델의 핵심이 됨

### Option C — 아웃박스/사가 기반 보장 모델 추가
- **한 줄 요약(Summary)**: 요청/결과/상태 변경을 별도 테이블과 워커로 분리해 보장 수준 강화
- **좋은 점(Pros)**:
  - 장애 복구 경로와 재처리 추적성이 매우 명확해짐
  - 장기적으로 다채널/다벤더 발송에 유리
- **아쉬운 점(Cons)**:
  - 현재 단계에서 구현/운영 복잡도 증가가 큼
  - 추가 저장소/워커/운영 비용 발생

#### Quick Compare (간단 비교)

| Option | 성능(Performance) | 안정성(Reliability) | 운영성(Operability) | 비용(Cost) | 개발(DevEx) | 결론(Verdict) |
|--------|----------------:|-----------------:|-----------------:|---------:|----------:|-------------|
| A | 중 | 중 | 중 | 중 | 하 | 미채택 |
| B | 상 | 상 | 상 | 상 | 상 | 채택 |
| C | 중 | 상 | 중 | 하 | 하 | 보류 |

---

## 4. Decision (최종 선택)

- **우리는 이것을 선택했다(Decision)**: **Option B — At-least-once + 상태전이 멱등**
- **한 줄 이유(One-liner)**: 외부 I/O 실패 가능성이 큰 Delivery 특성에서, 메시징 보장보다 상태전이 멱등 모델이 실제 복구/재처리에 더 현실적이고 일관적이기 때문.

---

## 5. Consequences (결과/영향)

### ✅ 좋아진 점(Pros)
- `READY/FAILED -> SENT` 선점 업데이트로 중복 소비 시에도 중복 발송을 방지
- `SENT -> SUCCEED/FAILED` 확정과 `attempt_count` 증가 규칙으로 재시도 경계 명확화
- 메시징/외부 I/O 실패를 상태 관점으로 흡수해 운영 판단 기준 통일

### ⚠️ 감수한 점(Cons)
- ack 시점과 발송 완료 시점이 분리되어 즉시 재소비 모델이 아님
- 상태 관측이 부족하면 지연 누적을 조기에 감지하기 어려움

### 🔧 운영 관점(Ops notes)
- 핵심 지표: `SENT` 체류량/체류시간, `FAILED` 전이율, 결과 이벤트 소비 지연
- 재대상 선별은 `READY/FAILED` + `attempt_count` 정책에 따라 수행

---

## 6. Evidence (증빙)

### Code references
- 요청 소비 후 비즈니스 처리와 ack: `contexts/dispatch/src/main/java/com/tr1l/delivery/infra/adapter/in/DispatchEventListener.java`
- 상태 선점 로직: `contexts/dispatch/src/main/java/com/tr1l/delivery/application/service/DeliveryService.java`
- 비동기 발송 + 결과 이벤트 발행: `contexts/dispatch/src/main/java/com/tr1l/delivery/application/service/DeliveryWorker.java`
- 상태 전이 SQL: `contexts/dispatch/src/main/java/com/tr1l/delivery/infra/adapter/out/DeliveryPersistenceAdapter.java`

### Measurements
- 성능 관측에서 결과 처리 병목을 스레드/소비량 조정으로 완화:
  - `delivery-result-handler-group` Lag `72.1K -> 211`
  - 소비량 `4K/m -> 17.2K/m`
- 근거 문서: `docs/50_performance/delivery/perf-delivery-0001.md`

