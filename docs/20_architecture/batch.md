---
title: 아키텍처
parent: TR1L (Overview)
nav_order: 21
---

# Batch Architecture 
> TR1L의 구조를 **"어떻게 설계했고(How)"**, **"어떻게 확장/운영 가능한지(Scalability/Operability)"** 관점에서 정리한다.<br/>
> 이 페이지에서는 근거/이유보다는 설계 자체를 서술한다.<br/>
> 근거/이유는 30_decisions 폴더에 작성한다.

---

# 일단 작성하면 나중에 Job끼리 분리함.


## 1) 한 눈에 보기

- **한 줄 요약**: [[ONE_LINER]]
- **키워드**: `[[K1]]` `[[K2]]` `[[K3]]`

---

## 2) 구조 (그림 1장)

```mermaid
flowchart LR
  A[[[[INPUT]]]] --> B[[[[CORE]]]] --> C[[[[OUTPUT]]]]
```

- **구성 요소**: [[COMPONENTS]]
- **흐름 요약**: [[FLOW_SUMMARY]]

---

## 3) 동작 흐름 (자세하게)

1. [[STEP_1]]
2. [[STEP_2]]
3. [[STEP_3]]

---

## 4) 운영/확장 포인트

- **확장(Scale)**: [[SCALE_POINT]]
- **운영(Operate)**: [[OPERATE_POINT]]
- **장애/재실행**: [[FAILURE_RERUN_POINT]]

---

## 5) 참고 (ADR)

- `30_decisions/[[ADR_1]]` — [[ADR_1_TITLE]]
- `30_decisions/[[ADR_2]]` — [[ADR_2_TITLE]]
