---
title: ADR-0007 인프라 아키텍처 - EC2 vs ECS/Fargate 전략
parent: 당위성/의사결정(Decisions)
nav_order: 16
---

<div class="tr1l-participants" aria-label="participants">
  <a class="tr1l-chip" href="https://github.com/Jsnooopy" aria-label="김도연 GitHub">
    <img class="tr1l-avatar" src="https://avatars.githubusercontent.com/u/108777654?v=4" alt="이재" />
    <span class="tr1l-name">이재</span>
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
> #### 작성일 : 2026-02-03


---
## 1. Context (상황/배경)
>여기는 배경 설명만 한다. **언제/어디서/규모(Scale)**, 그리고 당시 우리가 가진 제약 조건을 담는다.

- **어디 이야기인가요? (Scope)**: 전체 인프라 아키텍처 - API/Dispatch/Worker/Delivery 컴포넌트의 실행 환경
- **규모는 어느 정도인가요? (Scale)**: 
  - 월 100만 건 이상의 정산/발송 규모
  - 평시: 거의 유휴
  - 피크 슬롯: 폭발적 부하 증가(10배~100배)
- **피할 수 없는 조건은? (Constraints)**:
  - API는 상시 구동 필수 (안정성/운영 단순성)
  - Dispatch는 주기 실행 (슬롯 기반 선별)
  - Worker(Batch)는 필요할 때만 실행 (정산/청구서 생성)
  - Delivery는 특정 시간대에만 대량 확장 필수 (발송 처리)
  - 배포/재시작/스케일링을 빠르게 반복해야 함 (장애 대응)
- **현재 흐름은? (Current flow)**:
  - 모든 컴포넌트를 EC2로 운영하는 단순 구성을 초기 고려
  - 하나의 인스턴스 유형/구성으로는 상시 구동성과 폭발적 확장성을 동시에 만족할 수 없음

---

## 2. Problem (문제)
>지금 방식이 왜 힘든지, 어디서 위험해지는지를 적는다.  
보통은 **증상(Symptom)** → **원인(Root cause)** → **리스크(Risk)** 순서.

- **증상(Symptom)**: 
  - EC2 단독 운영 시 수평 확장(Scale-Out) 속도가 느림 (ASG/Launch Template/AMI 관리 부담)
  - 평시에도 "혹시나" 대비하여 인스턴스를 켜두는 비용 발생
  - 장애 발생 시 재배포/재시작/스케일링 반복 대응 필요한데 속도가 느림
- **원인(Root cause)**:
  - 업무 특성상 **컴포넌트별 운영 요구사항이 근본적으로 다름**
    - API: 상시 구동 + 예측 가능한 트래픽 → 고정 리소스 배치 적합
    - Delivery: 특정 시간 폭증 + 처리량 기반 확장 필수 → 동적 오케스트레이션 필수
    - Worker: 필요할 때만 실행 → 온디맨드 작업 실행 적합
  - 발송 처리(Delivery)의 병목이 가장 심각
    - 외부 발송 서비스의 지연/실패로 I/O 대기 시간 증가
    - CPU 사용률은 낮아도 처리량은 부족한 상황 빈번
    - 이런 상황에서 CPU/Memory 기반 오토스케일은 정확하지 않음
- **리스크(Risk)**:
  - 동적 확장 지연 → 발송 적체 증가 → SLA 미달
  - 평시 고정 비용 증가 → 비용 최적화 목표 달성 어려움
  - 부하 감소 시 스케일 인이 느려 불필요한 비용 발생
  - 배포 단순화 부족 → 운영 복잡도 증가 / 장애 대응 시간 증가

---

## 3. Options (대안)
>선택지는 **2~4개 정도**가 가장 좋다.  
각 옵션은 "한 문장 요약 + 핵심 포인트" 정도로만 정리하고, 깊은 반론(왜 버렸는지)은 `Rejected Alternatives`에 모아 링크.

### Option A — EC2 단독 운영 (Auto Scaling Group)
- **한 줄 요약(Summary)**: 모든 컴포넌트를 EC2 기반 ASG로 운영하며 CPU/Memory 기반 오토스케일링 활용
- **좋은 점(Pros)**:
  - 전체 스택을 하나의 패러다임으로 관리 (운영 개념 단순화)
  - 서버 인스턴스에 대한 직접 제어 가능 (세밀한 튜닝)
  - 기존 EC2 운영 경험 활용 가능
- **아쉬운 점(Cons)**:
  - ASG/Launch Template/AMI 관리 부담 증가
  - 스케일 아웃 속도가 느림 (인스턴스 부팅 3~5분)
  - 평시에도 "혹시나" 대비하여 인스턴스 최소 개수 유지 필수 → 비용 낭비
  - CPU/Memory 기반 스케일링은 발송(I/O 바운드) 특성을 정확히 반영하지 못함
  - 배포 시마다 AMI 빌드/업데이트 필요 → 배포 속도 저하
  - 장애 시 롤백/재배포 속도 느림

### Option B — ECS/Fargate 100% (전면 전환)
- **한 줄 요약(Summary)**: 모든 컴포넌트를 ECS/Fargate 기반 컨테이너로 운영하여 완전한 오케스트레이션 활용
- **좋은 점(Pros)**:
  - Fargate는 서버 관리 제거 (OS 패치/인스턴스 용량 계획 불필요)
  - Task 단위 빠른 시작 (10초 내외) → 동적 확장에 강함
  - 이미지 기반 배포 → 환경 차이 최소화 / 배포 속도 빠름
  - 서비스 단위 독립적 확장 가능 (Delivery만 10배 확장 가능)
  - Lag 기반 오토스케알링 구현 가능 (발송 부하 정확히 반영)
- **아쉬운 점(Cons)**:
  - 컨테이너/ECS 운영 학습곡선 존재
  - 세밀한 커널/호스트 레벨 튜닝 어려움
  - 관측 도구 구성 필요 (CloudWatch + 별도 모니터링)
  - 높은 네트워크 지연 발생 가능 (콜드 스타트)
  - API처럼 상시 구동하는 서비스는 불필요한 오케스트레이션 오버헤드

### Option C — 혼합 전략 (ECS/Fargate + EC2) ⭐ **선택**
- **한 줄 요약(Summary)**: 컴포넌트의 운영 특성에 따라 실행 환경 분리 (상시 구동은 EC2, 동적 확장은 Fargate)
- **좋은 점(Pros)**:
  - 각 컴포넌트의 운영 특성에 최적화된 인프라 선택
    - `API Server`: EC2 (상시 구동, 트래픽 예측 가능)
    - `Delivery (Consumer)`: ECS/Fargate (폭발적 확장, Lag 기반 스케일링)
    - `Dispatch/Worker`: ECS/Fargate RunTask (주기/온디맨드 실행)
  - 발송(병목)을 Fargate로 독립시켜 0~N 수평 확장 가능
  - API 안정성과 Delivery 확장성 동시 확보
  - 쓸데없는 낭비 없음 (필요한 부분만 오케스트레이션)
  - 이미지 기반 배포로 속도와 일관성 확보
  - 서비스/컴포넌트별 독립적인 장애 격리
- **아쉬운 점(Cons)**:
  - 운영 패러다임이 다름 (EC2 + Fargate 이중 학습)
  - 관측 도구를 여러 개 조합해야 함 (CloudWatch + 별도 모니터링)
  - 비용 구조 복잡 (고정 비용 + 변동 비용 혼재)

#### Quick Compare (간단 비교)

| Option | 수평 확장 | 배포 속도 | 운영 단순성 | 관찰성 | 비용 효율 | 안정성 | 결론 |
|--------|-------:|-------:|-------:|---:|-----:|-----:|-------|
| A (EC2 단독) | ⭐ | ⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐ | ⭐⭐⭐ | 불충분 |
| B (Fargate 100%) | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐⭐ | 과도 |
| C (혼합) | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | **최적** |

---

## 4. Decision (최종 선택)
>결론만을 딱 정리한다. "무엇을 선택했는지"가 한 번에 보이면 된다.

- **우리는 이것을 선택했다(Decision)**: **Option C — 혼합 전략 (ECS/Fargate + EC2)**
  
- **구체적 배치(Specific Deployment)**:
  - `api-server`: **EC2** (t3.medium 상시 구동)
  - `dispatch-server`: **ECS/Fargate RunTask** (EventBridge 스케줄 기반 주기 실행)
  - `worker (batch)`: **ECS/Fargate RunTask** (필요 시 온디맨드 실행)
  - `delivery (consumer)`: **ECS/Fargate Service** (Lag 기반 오토스케일링 0~N)

- **한 줄 이유(One-liner)**: 
  >*발송 처리(Delivery)의 폭발적 확장과 API의 안정적 상시 구동을 동시에 달성하면서, 평시 낭비 없는 비용 구조를 확보할 수 있어서*

- **추가 근거(Supporting Points)**:
  1. **발송 병목 타겟 확장**: Delivery만 Fargate로 격리하여 10배~100배 확장 가능
  2. **Cost Optimization**: 평시 낭비 제거 (Delivery 0명 가능 → 비용 0) + 피크 시에만 비용 발생
  3. **빠른 배포**: 이미지 기반 배포로 장애 대응 속도 향상
  4. **Lag 기반 스케일링**: CPU/Memory 기반이 아닌 실제 부하(Backlog) 기반 확장 가능
  5. **서비스 격리**: Delivery 장애가 API/Dispatch로 전파되지 않음

---

## 5. Trade-off & 주의사항

### Trade-off
- **운영 복잡도 증가**: EC2와 Fargate 두 패러다임 동시 관리
  - 완화책: 명확한 역할 분담 + 문서화로 학습곡선 단축
- **관측 도구 다원화**: CloudWatch + 별도 모니터링 필수
  - 완화책: 통합 대시보드 구성으로 단일 뷰 제공
- **비용 구조 복잡**: 시간 기반(EC2) + 사용량 기반(Fargate) 혼재
  - 완화책: 비용 분석 자동화 및 정기 리뷰

### 주의사항
- **로깅/트레이싱**: 분산 환경이므로 중앙화된 로깅 필수 (ECS + CloudWatch + custom logging)
- **버전 관리**: 이미지 기반 배포는 이미지 버전 관리 영구 필수
- **네트워크 격리**: Fargate Task 간 통신 시 보안 그룹/네트워크 정책 설정 필요
- **Cold Start**: Fargate 시작이 빠르지만 여전히 JVM 워밍업 필요 (5~10초)
  - 완화책: 사전 워밍업 또는 프리워밍 패턴 검토

---

## 6. 구현 상세 (Implementation Details)

### Lag 기반 오토스케일링
- **문제 정의**: CPU/Memory 기반 스케일은 발송(I/O 바운드) 특성 미반영
- **해결책**:
  - Kafka Consumer Lag을 CloudWatch 지표로 수집
  - Lag > 임계값 시 Delivery Task 증가
  - Lag < 임계값 시 Task 감소 (scale-down cooldown: 5분)
  - 우선 알람 기반 수동 스케일링으로 시작 → 자동화 점진적 확대

### 배포 전략
- **Blue-Green 배포**: ECS 스케줄 태스크 업데이트 → 롤링 배포
- **Canary 배포**: 초기 1개 Task로 검증 후 전체 확대 가능
- **Rollback**: 이전 이미지 버전 즉시 배포로 빠른 복구

### 모니터링/관찰성
- **핵심 지표**:
  - Delivery Consumer Lag (발송 적체)
  - Consumer 처리량 (msg/sec)
  - 외부 발송 지연 분포
  - Task 시작/종료 시간 분포
- **알람**:
  - Lag > 5000 → 즉시 알림 (스케일 트리거)
  - Consumer 에러율 > 1% → 즉시 알림
  - Task 시작 실패 → 즉시 알림

---

## 7. 향후 검토 항목 (Future Considerations)

1. **완전 마이그레이션**: API도 Fargate로 전환 시 이점 재평가
2. **Karpenter 도입**: EC2 기반 노드 풀 자동 관리
3. **Service Mesh (Istio/Linkerd)**: 분산 서비스 간 트래픽 관리 고도화
4. **이벤트 기반 스케일링**: Lambda + Kafka 트리거로 더 빠른 대응
5. **다중 가용영역(Multi-AZ)**: 고가용성을 위한 Zone 분산 배치



