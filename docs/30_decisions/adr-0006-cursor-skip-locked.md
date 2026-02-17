<div class="tr1l-participants" aria-label="participants">
  <a class="tr1l-chip" href="https://github.com/cylin0201" aria-label="김원기 GitHub">
    <img class="tr1l-avatar" src="https://github.com/cylin0201.png?size=120" alt="김원기" />
    <span class="tr1l-name">김원기</span>
  </a>
</div>

<style>
  .tr1l-participants{
    display:flex; flex-wrap:wrap; gap:10px;
    align-items:center; margin:8px 0 2px;
  }
  .tr1l-chip{
    display:inline-flex; align-items:center; gap:10px;
    padding:8px 12px; border-radius:999px;
    text-decoration:none !important;
    border:1px solid rgba(127,127,127,.28);
    background:rgba(127,127,127,.10);
    backdrop-filter:saturate(140%) blur(4px);
    transition:transform .12s ease, border-color .12s ease, background .12s ease;
  }
  .tr1l-chip:hover{
    transform:translateY(-1px);
    border-color:rgba(127,127,127,.45);
    background:rgba(127,127,127,.14);
  }
  .tr1l-avatar{
    width:28px; height:28px; border-radius:50%;
    display:block; flex:0 0 auto;
    box-shadow:0 0 0 1px rgba(127,127,127,.22);
  }
  .tr1l-name{
    font-weight:650; font-size:14px;
    line-height:1; letter-spacing:-0.2px;
    color:inherit;
  }
</style>
> #### 작성일 : 2026-02-08

---

## 1. Context (상황/배경)

- **어디 이야기인가요? (Scope)**: Dispatch Orchestration (Producer) — `billing_targets` 후보 조회 및 “작업 분배” 방식
- **규모는 어느 정도인가요? (Scale)**:
    - 2시간 주기 오케스트레이션
    - 최대 100만 건 발송 후보 처리 가능(READY/FAILED 재시도 포함)
- **피할 수 없는 조건은? (Constraints)**:
    - 동일 주기 내에서 **여러 인스턴스/재실행** 가능 (운영상 중복 실행 리스크 존재)
    - 후보 조회와 발행은 **짧은 시간에 대량 처리** (Hotspot)
    - 동일 row를 여러 워커가 집어가면 **중복 발행/중복 처리**로 이어질 수 있음
    - READY/FAILED만 대상으로 하고, 재시도는 `attempt_count <= maxAttemptCount`로 제한
    - 금지 시간(Blackout) 고려로 `availableTime <= now` 조건 필요

- **현재 흐름은? (Current flow)**:
    - Dispatch Server가 후보를 DB에서 조회
    - 조회된 후보를 Kafka Topic(발송 요청)으로 발행
    - (별도 Consumer/Worker가 발송 및 결과 처리)

---

## 2. Problem (문제)

- **증상(Symptom)**:
    - Producer 인스턴스가 2개 이상이거나, 재실행이 겹치면 **같은 발송 후보를 중복으로 가져가는 현상**이 발생 가능
    - 단순 페이징(OFFSET)이나 락 없는 SELECT는 동시성 상황에서 “작업 분배”가 깨짐
- **원인(Root cause)**:
    - 후보를 “읽기만” 하면 다른 트랜잭션이 같은 row를 동시에 읽어 처리할 수 있음
    - OFFSET 기반 페이징은 대량 테이블에서 비용이 크고, 동시 업데이트가 섞이면 스킵/중복이 발생하기 쉬움
- **리스크(Risk)**:
    - 중복 발행 → Consumer 중복 처리 방어가 있더라도 **불필요한 트래픽/비용 증가**
    - 일부 후보는 경합으로 계속 밀려 **처리 지연/기아(starvation)**가 생길 수 있음
    - Producer 확장(scale-out) 시 “안전한 분할 처리”가 안 되면 운영이 어려움

---

## 3. Options (대안)

### Option A — `FOR UPDATE SKIP LOCKED` + Cursor Paging (user_id 커서)
- **한 줄 요약(Summary)**: row-level lock으로 “가져간 후보”를 잠그고, 다른 인스턴스는 `SKIP LOCKED`로 건너뛰어 **자연스러운 작업 분배**를 만든다.
- **좋은 점(Pros)**:
    - 다중 인스턴스에서도 동일 row 중복 선점 방지(락 기반)
    - `SKIP LOCKED`로 블로킹 없이 다음 후보로 진행 → 처리량/안정성에 유리
    - Cursor Paging(`user_id > :lastUserId`)로 OFFSET 대비 성능 안정적
- **아쉬운 점(Cons)**:
    - 트랜잭션 범위/락 유지 시간 관리가 중요(너무 길면 락 점유 증가)
    - 커서 키(`user_id`)와 조건 컬럼에 맞춘 인덱스 설계가 필요

### Option B — 락 없는 Cursor Paging (`user_id > :lastUserId`)만 사용
- **한 줄 요약(Summary)**: 성능은 좋지만 동시 실행 시 중복 선점이 발생할 수 있어 “작업 분배” 보장이 약함.
- **좋은 점(Pros)**:
    - 구현이 단순하고 DB 락 부담이 적음
- **아쉬운 점(Cons)**:
    - 다중 인스턴스/재실행에서 동일 후보 중복 발행 가능성이 높음

### Option C — Offset Paging + 상태 선반영(예: DISPATCHING) 업데이트
- **한 줄 요약(Summary)**: “읽고 바로 상태를 바꿔 점유”하는 전략이지만, OFFSET 비용 + 상태 전이 복잡도(부분 실패)가 커짐.
- **좋은 점(Pros)**:
    - 중복 방지 의도를 상태로 명시 가능
- **아쉬운 점(Cons)**:
    - OFFSET 페이징 비용/불안정(대량에서 느림)
    - 부분 실패(업데이트 성공/발행 실패 등) 시 복구 로직이 복잡
    - 요구사항상 DISPATCHING 제거 방향과 충돌

---

## 4. Decision (최종 선택)

- **우리는 이것을 선택했다(Decision)**: **Option A — `FOR UPDATE SKIP LOCKED` + Cursor Paging**
- **한 줄 이유(One-liner)**: Producer를 수평 확장하거나 재실행이 겹쳐도, DB 락과 `SKIP LOCKED`로 후보를 자동 분할하여 **중복 발행을 최소화**하고 **지연 없이 처리**한다.

---

## 5. Consequences (결과/영향)

✅ 좋아진 점(Pros)
- 다중 인스턴스 환경에서 “작업 분배”가 자연스럽게 성립
- 블로킹 없이 다음 row로 진행하여 처리량/안정성이 개선
- OFFSET 대비 성능 예측 가능(대량에서도 일정)

### ⚠️ 감수한 점(Cons)
- 트랜잭션 범위를 짧게 유지해야 함(락 점유 최소화)
- 커서/조건에 맞춘 인덱스가 사실상 필수

### 🔧 운영 관점(Ops notes)
- DB: 락 대기/락 점유 시간, SKIP LOCKED 스캔량 모니터링
- App: 페이지당 처리시간, 처리 건수, Kafka 발행 실패율/재시도율 관측
- 설정: `pageSize`, `maxAttemptCount`, (필요 시) 런당 상한을 구성값으로 관리

---

## 6. Evidence (증빙)

- 본 ADR은 “동시 실행/재실행이 가능한 Producer”라는 운영 리스크를 전제로, Postgres가 제공하는 row lock + `SKIP LOCKED`를 통해 **중복 선점 문제를 구조적으로 제거**하는 방향으로 선택했다.
- 별도의 성능 벤치마크 수치는 없으며, TR1L의 처리 패턴(대량 후보 + 병렬 가능성 + DISPATCHING 제거)에 대한 적합성을 기준으로 결정했다.
