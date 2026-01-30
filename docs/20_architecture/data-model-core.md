---
title: 핵심 데이터 모델(Data Model Core)
parent: 아키텍처(Architecture)
grand_parent: TR1L (Overview)
nav_order: 23
---

# 핵심 데이터 모델(Data Model Core)

## 1) 핵심 테이블/컬렉션 목록
| 구분 | 저장소 | 이름 | 키/유니크 | 역할 |
|---|---|---|---|---|
| 정산 대상 | RDS | billing_targets | (billing_month,user_id) | flatten 결과 |
| 작업 상태 | RDS | billing_work | (billing_month,user_id) | step 상태/결과 |
| 발송 준비 | RDS | delivery_ready/result | message_id | destination + s3_key |
| 결과물 | S3 | invoice artifacts | invoice_key | 템플릿 결과 |

## 2) 상태 모델(State Model)
- 배치 상태:
- 카프카 상태:
- 전이 규칙:

## 3) 인덱스/파티셔닝
- 파티셔닝 키:
- 필수 인덱스:
