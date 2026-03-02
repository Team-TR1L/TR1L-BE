---
title: 아키텍처
parent: TR1L (Overview)
nav_order: 21
---

# Infrastructure Architecture 
> TR1L의 구조를 **"어떻게 설계했고(How)"**, **"어떻게 확장/운영 가능한지(Scalability/Operability)"** 관점에서 정리한다.<br/>
> 이 페이지에서는 근거/이유보다는 설계 자체를 서술한다.<br/>
> 근거/이유는 30_decisions 폴더에 작성한다.


## 1) 한 눈에 보기

- **한 줄 요약**: 컴포넌트 별 운영 특성에 맞춘 EC2 + ECS/Fargate 혼합 인프라
- **키워드**: `혼합 인프라` `Lag 기반 오토스케일` `비용 최적화`

---

## 2) 구조 (그림 1장)


<figure class="tr1l-figure">
  <img src="../images/infra.png" alt="infra" loading="lazy" />
  <figcaption>Infrastructure Architecture - EC2 + ECS/Fargate 혼합 구성</figcaption>
</figure>

- **구성 요소**: API 서버(EC2), Dispatch/Worker(Fargate RunTask), Delivery Consumer(ECS/Fargate), Kafka, S3/MongoDB
- **흐름 요약**: API/Dispatch가 이벤트를 생산 → Kafka 큐 → Delivery 컨슈머가 소비하여 발송 처리; 배치 Job은 필요 시 Fargate로 실행하여 데이터 생성/저장

---

## 3) 동작 흐름 (자세하게)

1. API 서버(EC2)가 운영/관리 요청과 상태 조회를 처리한다.
2. Dispatch/Worker는 EventBridge 스케줄 또는 수동 트리거로 Fargate Task를 실행해 정산/선별/파일생성을 수행한다.
3. 발송 요청은 Kafka에 쌓이고, Delivery 컨슈머(ECS/Fargate)가 Lag 기반 자동 스케일링으로 처리한다.

---

## 4) 운영/확장 포인트

- **확장(Scale)**: Delivery 성능 병목은 Kafka Lag 기반 오토스케일링으로 대응. Fargate 태스크를 0~N으로 유연하게 늘리고 줄인다.
- **운영(Operate)**: API 서버는 EC2로 상시 운영, 배치/Dispatch는 RunTask로 온디맨드 실행. 이미지 기반 배포로 일관성 유지.
- **장애/재실행**: 컨슈머 장애 시 Lag가 증가하면 자동으로 새로운 태스크가 시작되고, Dispatch/Worker는 재시작이 용이해 운영 복구가 간단하다.

---

## 5) 참고 (ADR)

- `30_decisions/adr-0007-infrastructure.md` — ADR-0007 인프라 아키텍처 선택 (EC2 + ECS/Fargate)

