---
title: 재실행/게이팅(Idempotency & Gating)
parent: 아키텍처(Architecture)
grand_parent: TR1L (Overview)
nav_order: 24
---

# 재실행/게이팅(Idempotency & Gating)

## 1) 목적(Why)
- 배치 2회 실행 시 중복 생성 금지
- 발송 실패 재처리 시 중복 발송 금지

## 2) 게이트 키(Gating Keys)
- billing_cycle:
- format_cycle:
- delivery_cycle:

## 3) 상태 전이(State Machine)
- 상태 정의:
- 전이 조건:
- 재시도/재처리 조건:

## 4) 구현 전략(How)
- DB unique + upsert
- 상태 테이블 분리
- replay 제한 규칙
