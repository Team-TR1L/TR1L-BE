---
title: 50. Performance (성능/결과)
parent: 00. Overview (개요)
nav_order: 50
---

## 예시 템플릿!!!! 이거 복붙해서 쓰셈 <- 이거는 꼭 지우고 PR ㄱㄱ

# Performance Results

이 문서는 TR1L에서 성능을 **어떻게 측정했고**, **무엇을 바꿨고**, **어떻게 검증했는지**를 남깁니다.  
숫자가 아직 없더라도, 최소한 “측정 가능한 형태”로 남기는 게 목표입니다.

---

## 1) What was slow (병목)
- 병목 지점:


---

## 2) Measurement (측정)
### Environment
- DB 스펙:
- App 스펙:
- Dataset scale:

### Metrics (사진 있으면 좋음)
- Step duration (avg/p95)
- DB connections / lock wait / read I/O
- Query plan (EXPLAIN, BUFFERS)

---

## 3) What we changed (개선)
- 변경 사항:
- 관련 ADR:

---

## 4) Results (결과)
| Metric              | Before | After | Improvement |
|---------------------|-------:|------:|------------:|
| Job duration        |        |       |             |
| Step01              |        |       |             |
| Step03 p95          |        |       |             |
| DB connections peak |        |       |             |

---

## 5) Artifacts (증거 링크)
- SQL (before/after):
- EXPLAIN outputs:
- Grafana screenshots:
- Repro steps:

---

## 6) Next (다음 병목)(선택)
- 다음 개선 후보:
- 검증 계획:
