
<div class="tr1l-participants" aria-label="participants">
  <a class="tr1l-chip" href="https://github.com/tkv00" aria-label="김도연 GitHub">
    <img class="tr1l-avatar" src="https://github.com/tkv00.png?size=120" alt="김도연" />
    <span class="tr1l-name">김도연</span>
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
> #### 작성일 : 2026-01-28


---

## 1. Context (상황/배경)
>여기는 배경 설명만 한다. **언제/어디서/규모(Scale)**, 그리고 당시 우리가 가진 제약 조건을 담는다.

- **어디 이야기인가요? (Scope)**: (예: Job1-Step3 / Job2-Step1 / API / Dispatch / Storage)
- **규모는 어느 정도인가요? (Scale)**: (예: 1,000,000 users / 월 정산 / N items)
- **피할 수 없는 조건은? (Constraints)**: (예: rerun 필수, 외부 I/O(S3) 실패 가능, 운영 window 제한)
- **현재 흐름은? (Current flow)**: (현 상태를 2~3줄로 요약)

---

## 2. Problem (문제)
>지금 방식이 왜 힘든지, 어디서 위험해지는지를 적는다.  
보통은 **증상(Symptom)** → **원인(Root cause)** → **리스크(Risk)** 순서.

- **증상(Symptom)**: (예: 처리시간이 들쭉날쭉, tail latency 증가, lock/timeout 증가, 비용 상승)
- **원인(Root cause)**: (예: join explosion, write pattern, offset scan, external retry storm)
- **리스크(Risk)**: (예: 중복 청구/발송, SLA 미달, 장애 확산, 운영 부담 폭증)

---

## 3. Options (대안)
>선택지는 **2~4개 정도**가 가장 좋다.  
각 옵션은 “한 문장 요약 + 핵심 포인트” 정도로만 정리하고, 깊은 반론(왜 버렸는지)은 `Rejected Alternatives`에 모아 링크.

### Option A — (이름/Approach)
- **한 줄 요약(Summary)**:
- **좋은 점(Pros)**:
- **아쉬운 점(Cons)**:
- **왜 버렸나요? (Link)**: [Rejected - A](./02_rejected-alternatives.md#a)

### Option B — (이름/Approach)
- **한 줄 요약(Summary)**:
- **좋은 점(Pros)**:
- **아쉬운 점(Cons)**:
- **왜 버렸나요? (Link)**: [Rejected - B](./02_rejected-alternatives.md#b)

### Option C — (이름/Approach)
- **한 줄 요약(Summary)**:
- **좋은 점(Pros)**:
- **아쉬운 점(Cons)**:

#### Quick Compare (간단 비교)

| Option | 성능(Performance) | 안정성(Reliability) | 운영성(Operability) | 비용(Cost) | 개발(DevEx) | 결론(Verdict) |
|--------|----------------:|-----------------:|-----------------:|---------:|----------:|-------------|
| A      |                 |                  |                  |          |           |             |
| B      |                 |                  |                  |          |           |             |
| C      |                 |                  |                  |          |           |             |

---

## 4. Decision (최종 선택)
>결론만을 딱 정리한다. “무엇을 선택했는지”가 한 번에 보이면 된다.

- **우리는 이것을 선택했다(Decision)**: **Option (A/B/C)**
- **한 줄 이유(One-liner)**: (예: “처리시간을 예측 가능하게 만들고(rerun-safe), 운영을 단순화할 수 있어서”)

---

## 5. Consequences (결과/영향)
>결정은 항상 대가가 따른다. 좋은 점만 쓰면 오히려 신뢰도가 떨어진다.  
운영 관점 변화(모니터링/알림/복구 난이도)가 있으면 같이 적는다.

### ✅ 좋아진 점(Pros)
- (예: Step3의 join 제거 → DB 부하 감소, 처리시간 안정화)
- (예: 실패 구간 분리 → rerun 범위가 명확해짐)

### ⚠️ 감수한 점(Cons)
- (예: 추가 저장/쓰기 비용)
- (예: 스키마/마이그레이션 관리 필요)
- (예: 구현 복잡도 증가)

### 🔧 운영 관점(Ops notes)
- (예: 대시보드 패널 추가, 알림 임계치 조정, runbook 업데이트)

---

## 6. Evidence (증빙)
>“이 선택이 맞다”는 말로 끝내지 말고, 우리가 실제로 확인한 근거를 남긴다.  
가능하면 **전/후(Before/After)** 또는 **대안 비교** 중 하나는 꼭 넣는다.

### Measurements (전/후 비교)
- **Step duration**: Before ___ → After ___ (Improvement ___%)
- **DB 부하(CPU/IO/locks/connections)**: Before ___ → After ___
- **처리량(Throughput)**: Before ___ → After ___

### Grafana / Metrics
- **Dashboard**:
- **Key panels**:
- **Screenshot/Link**:

### EXPLAIN / Query Plan
- **Query**:
- **Plan summary**:
- **Notes**:

### Logs / State
- (예: claim 성공/NOOP 로그, billing_cycle/format_cycle 상태 스냅샷)
