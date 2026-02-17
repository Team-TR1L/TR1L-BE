---
title: ADR-0003 스냅샷 저장소(Snapshot Store) 선택 - Mongo vs RDS
parent: 당위성/의사결정(Decisions)
nav_order: 12
---

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
> #### 작성일 : 2026-02-02


---

## 1. Context (상황/배경)
>여기는 배경 설명만 한다. **언제/어디서/규모(Scale)**, 그리고 당시 우리가 가진 제약 조건을 담는다.

- **어디 이야기인가요? (Scope)**: 
  - CalculateJob(Job1) - Step3(청구서 정산 + 결과 스냅샷 저장)
  - FormatJob(Job2) - Step2(스냅샷 조회 → 렌더링 → S3 업로드)
- **규모는 어느 정도인가요? (Scale)**: 
  - 월 정산 대상 : 최대 **1,000,000** rows (유저 1명당 1개의 청구서)
  - 스냅샷: user-month 단위 1건으로 대량 쓰기/대량 읽기 패턴
- **피할 수 없는 조건은? (Constraints)**: 
  - 재시도 필수: 실패/재시도 시 중복 청구/중복 발송/청구서 중복 생성 및 저장이 절대 발생하면 안됨
  - 외부 I/O(S3) 실패 가능성: **청구서 생성 작업**과 **청구서 완성본 제작 및 업로드 작업**은 분리되어야 함
  - 비용 최적화: 월 초 청구서 생성 시(트래픽 피크)에만 worker가 스케일 아웃되어야 함
- **현재 흐름은? (Current flow)**: 
  - Step1에서 대상/정책 입력을 'billing_targets' 테이블로 평탄화
  - Step3에서 계산 결과를 '스냅샷 저장소'에 1건 단위로 저장
  - Job2는 청구서 스냅샷을 조인 없이 조회하여 실제 청구서 템플릿(SMS/Email)로 포맷팅 및 S3 업로드 수행

---

## 2. Problem (문제)
>지금 방식이 왜 힘든지, 어디서 위험해지는지를 적는다.  
보통은 **증상(Symptom)** → **원인(Root cause)** → **리스크(Risk)** 순서.

- **증상(Symptom)**:
  - Job2(렌더링/S3 업로드)가 청구서 완성본을 만들기 위해 **다중 테이블 조인/다중 쿼리**가 필요하면 처리량이 불안정해지고 tail latency가 증가
  - 계산은 끝났는데 S3/발송이 실패하는 경우가 반복되면, “결과 재생성” 비용이 누적되어 재시도 범위가 커짐
  - 월말 피크에 Postgres가 (조인/상태 업데이트/사용량 조회)로 이미 바쁜데, 여기에 “결과 저장/조회”까지 합쳐지면 병목이 한곳에 집중
- **원인(Root cause)**:
  - 청구 결과는 부가 서비스 이용 내역/할인 내역등 **계층 구조(문서형)**이며, 정책/요구사항 변화로 **스키마 변화**가 잦음
  - 결과를 관계형으로 정규화하면 “테이블 수 증가 + 조인 재등장”으로 Job2가 결국 병목 구간이 될 가능성 존재
  - 결과를 같은 Postgres에 JSONB로 저장하더라도, 월말 피크에서 **IO 부하**가 기존 워크로드와 결합되어 장애 반경이 커짐
- **리스크(Risk)**:
  - 중복 청구/중복 발송(멱등성 실패) 또는 누락 발생
  - 장애 확산(한 DB 병목이 전체 파이프라인을 멈춤)
  - 재처리 비용 폭증(“계산부터 다시”가 되는 구조), 운영 부담(장애 원인 분리가 어려움)

---

## 3. Options (대안)
>선택지는 **2~4개 정도**가 가장 좋다.  
각 옵션은 “한 문장 요약 + 핵심 포인트” 정도로만 정리하고, 깊은 반론(왜 버렸는지)은 `Rejected Alternatives`에 모아 링크.

### Option A — MongoDB(Document Snapshot Store)
- **한 줄 요약(Summary)**:
  - 청구 결과를 year-month 기준으로 유저 1명당 1건으로 저장하여 Job2에서 조인 없이 1회 조회로 고정
- **좋은 점(Pros)**:
    - **문서형 결과**부가 서비스 이용 내역/할인 내역에 자연스럽게 맞는다.
    - Job2는 **키 기반 단건 조회**만 수행 → 조인/다중 쿼리 제거로 처리량이 예측 가능.
    - 스키마 변화 시 **RDB 정규화/마이그레이션 비용**을 최소화할 수 있다(문서 구조 확장 + schemaVersion).
    - Postgres에 “조인/상태/사용량” 부하가 몰리는 월말 피크에서 **스냅샷 read/write를 격리**해 장애 반경을 줄인다.
    - 재시도/S3 재업로드 시 “계산 재수행” 없이 **기존 스냅샷**으로 복구 범위를 줄일 수 있다.
- **아쉬운 점(Cons)**:
  - 저장소 1개가 늘어나 운영 난이도가 커진다(백업/모니터링/알림)
  - 문서 크기/인덱스/쓰기 패턴을 잘못 잡으면 비용·지연이 증가 가능성 존재
- **왜 버렸나요? (Link)**: 해당 없음-채택된 Option

### Option B — RDS(PostgreSQL) JSONB (동일 클러스터 내 저장)
- **한 줄 요약(Summary)**:
  - “문서 스냅샷”을 Postgres `jsonb`로 저장해 엔진을 통일
- **좋은 점(Pros)**:
  - 엔진/운영 스택 단일화(권한/백업/모니터링)
  - 구현 난이도가 낮음
- **아쉬운 점(Cons)**:
  - 월 초 피크에서 Postgres가 담당하는 조인/상태/사용량 작업에 청구서 스냅샷 write/read가 결합됨.
  - 결과적으로 IO 변동성이 커져 tail latency가 흔들릴 위험 증가 가능성
- **왜 버렸나요? (Link)**: [Rejected - B](./02_rejected-alternatives.md#opt-b-same-rds)

### Option C — RDS(Postgres) 별도 클러스터(스냅샷 전용) + JSONB
- **한 줄 요약(Summary)**:
  - 스냅샷 전용 RDS를 따로 생성하여 격리 환경은 확보하되, 저장 방식은 JSONB
- **좋은 점(Pros)**:
  - 메인 Postgres(조인/상태)와 스냅샷 부하를 분리 가능(Option B의 핵심 문제 일부 해결).
  - SQL/운영 재사용.
- **아쉬운 점(Cons)**:
  - 문서 워크로드를 Postgres가 계속 담당하므로, 결국 **WAL/Autovacuum/bloat/tuning** 영역의 부담이 남는다.
  - 격리를 위해 DB를 늘리는 비용을 이미 지불한다면, **문서 스냅샷에 더 자연스러운 저장소**를 쓰는 것이 합리적이다.
- **왜 버렸나요? (Link)**: [Rejected - C](./02_rejected-alternatives.md#opt-c-different-rds)

#### Quick Compare (간단 비교)

| Option                    | 성능(Performance) | 안정성(Reliability) | 운영성(Operability) | 비용(Cost) | 개발(DevEx) | 결론(Verdict) |
|---------------------------|----------------:|-----------------:|-----------------:|---------:|----------:|-------------|
| A (Mongo)                 |               상 |                상 |                중 |        중 |         상 | 채택          |
| B (Same RDS + JSONB)      |               중 |                하 |                상 |        상 |         중 | 기각          |
| C (Dedicated RDS + JSONB) |             중~상 |              중~상 |                중 |        중 |         중 | 기각          |

---

## 4. Decision (최종 선택)
>결론만을 딱 정리한다. “무엇을 선택했는지”가 한 번에 보이면 된다.

- **우리는 이것을 선택했다(Decision)**: **Option A - MongoDB(Document Snapshot Store)**
- **한 줄 이유(One-liner)**: 
  - “스냅샷은 관계형 이점이 거의 없는 ‘문서 단위 결과’이므로, Job2를 ‘조인 없이 1회 조회’로 고정하고, 월초 피크에서 Postgres(조인/상태)와 스냅샷 부하를 분리해 성능 변동과 장애 확산을 최소화하기 위해 MongoDB를 채택했다.”

---

## 5. Consequences (결과/영향)
>결정은 항상 대가가 따른다. 좋은 점만 쓰면 오히려 신뢰도가 떨어진다.  
운영 관점 변화(모니터링/알림/복구 난이도)가 있으면 같이 적는다.

### ✅ 좋아진 점(Pros)
- **Job2 병목 제거**: 결과 조합을 위한 조인/다중 쿼리 제거 → “단건 조회 → 렌더링”으로 고정
- **워크로드 격리**: Postgres(조인/상태/사용량)와 Snapshot Store를 분리하여 월말 피크의 장애 범위 축소
- **rerun 내구성**: S3/발송 실패 시 “계산부터 재수행”이 아니라 스냅샷 포맷팅+업로드 재처리 범위를 최소화
- **스키마 진화 대응**: 정책/표기 변경 시 RDB 정규화 재설계보다 문서 확장(schemaVersion)으로 빠르게 대응

### ⚠️ 감수한 점(Cons)
- 저장소 추가로 운영 표면적 증가(백업/모니터링/알림)
- 문서 크기/인덱스/쓰기 패턴 가이드 필요(키 조회 중심으로 최소 인덱스)

---

## 6. Evidence (증빙)
>“이 선택이 맞다”는 말로 끝내지 말고, 우리가 실제로 확인한 근거를 남긴다.  
가능하면 **전/후(Before/After)** 또는 **대안 비교** 중 하나는 꼭 넣는다.

### Measurements (전/후 비교)
- **Job2 조회 비용 모델 변화**
  - Before: 조인/다중 쿼리로 “결과 조합” 필요
  - After: (billing_month, user_id) 키 기반 문서 **1회 조회**
- **DB 병목 격리 효과**
  - Before: Postgres에 조인/상태/사용량 + 스냅샷 write/read가 결합
  - After: 스냅샷 write/read를 Snapshot Store로 분리하여 Postgres 병목이 전체 장애로 확산되는 가능성 차단


