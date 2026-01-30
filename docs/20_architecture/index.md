---
title: 아키텍처 목차(Index)
parent: TR1L (Overview)
nav_order: 20
---

# Architecture Index
> TR1L의 구조를 **"어떻게 설계했고(How)"**, **"어떻게 확장/운영 가능한지(Scalability/Operability)"** 관점에서 정리한다.

# 핵심 설계 포인트
- Modular Monolith + DDD + Hexagonal
- Idempotency/Gating (rerun-safe)
- Batch Pipeline(Job1/Job2)
- Observability-first

# Documents
- [Idempotency & Gating](idempotency-gating.md)
- [Scaling Strategy](scaling-strategy.md)