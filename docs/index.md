---
title: 홈(Home)
nav_order: 1
---

# TR1L 기술 문서 (Engineering Docs)

TR1L은 **대규모 청구/정산(Billing) + 발송(Delivery)**을 전제로  
**성능(Performance)**, **장애 내성(Reliability)**, **운영 관측(Observability)**을 중심으로 설계한 백엔드 프로젝트입니다.

## 추천 읽는 순서(Recommended Path)
1) **프로젝트 개요(Overview)** → 2) **아키텍처(Architecture)** → 3) **운영(Operations)** → 4) **성능 결과(Performance)** → 5) **당위성/의사결정(ADR)**

---

## Quick Links
- [프로젝트 개요(Overview)](./00_overview.md)

### 아키텍처(Architecture)
- [멱등성 & 게이팅(Idempotency & Gating)](./20_architecture/idempotency-gating.md)
- [확장 전략(Scaling Strategy)](./20_architecture/scaling-strategy.md)

### 운영(Operations)
- [운영 가이드(Runbook)](./40_operations/runbook.md)
- [관측/모니터링(Observability)](./40_operations/observability.md)

### 성능(Performance)
- [성능 결과(Performance Results)](./50_performance/perf-results.md)

### 당위성/의사결정(Decisions / ADR)
- [ADR 목차(Index)](./30_decisions/adr-index.md)
- [설계 원칙(Design Principles)](./30_decisions/00_design-principles.md)
- [결정 프레임워크(Decision Framework)](./30_decisions/01_decision-framework.md)
- [버린 선택지(Rejected Alternatives)](./30_decisions/02_rejected-alternatives.md)
