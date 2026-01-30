---
title: 확장 전략(Scaling Strategy)
parent: 아키텍처(Architecture)
grand_parent: TR1L (Overview)
nav_order: 25
---

# 확장 전략(Scaling Strategy)

## 1) 스케일 가정(Assumptions)
- 월 처리량:
- 피크 시간:
- 배치 운영 윈도우:

## 2) 트리거(Triggers)
- EventBridge Scheduler → ECS Task 실행
- Kafka Lag Metric → Consumer Auto Scaling

## 3) 결정해야 하는 설정(Load Test Needed)
- 파티션 수:
- 컨슈머 수:
- batch chunk / writer 방식:
- DB connection pool:

## 4) 부하 테스트 계획(요약)
- 시나리오:
- 목표 지표:
- 성공 조건:
