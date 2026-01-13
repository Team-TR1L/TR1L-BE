# Billing & Notification Platform

대규모 청구/정산 데이터를 기반으로 고객별 청구서를 생성하고, 메시지 발송(Email/SMS)을 **중복 없이 안정적으로 처리**하는 플랫폼입니다.  
특히 “월 100만 건 이상”의 정산/발송 규모를 전제로 **배치 재실행 가능성**, **장애 내성**, **수평 확장**, **비용 최적화**에 초점을 맞추었습니다.

---

## 핵심 요구사항

- 특정일에 **전체 정산 완료**
- 고객이 정한 날짜에 고객별 청구서 발송
- 고객은 “수신 거부 시간대(2시간 슬롯)”를 중복 선택 가능  
  → 시스템은 **2시간 단위 슬롯**으로 발송 작업을 주기 실행
- 배치가 중복 실행되더라도 정산 결과가 중복 생성되지 않아야 함(멱등성)
- 발송이 중복 발송되지 않아야 함(동시성 제어)
- 실제 발송은 Mocking 처리(예: 1초 지연 후 성공, 1% 실패) + 실패 건은 재시도/대체 채널로 전환 가능

---

## 시스템 구성(요약)

- **Batch**: 정산 스냅샷 생성 → 발송용 파일 생성(S3 업로드)
- **Dispatcher(스케줄러)**: 2시간마다 “이번 슬롯 발송 대상”을 선별하여 Kafka로 이벤트 발행
- **Consumer(발송 서버)**: Kafka 이벤트 소비 → S3 청구서 조회 → 발송(Mock) → 상태 업데이트
- **Monitoring/Auto Scaling**: Kafka Lag 기반 지표 수집 → CloudWatch Alarm → Consumer 수평 확장(0~N)

---

## 기술 선택 근거

### Database: PostgreSQL
본 시스템은 단순 CRUD보다 **정합성과 동시성 제어**, 그리고 **복잡한 집계/조건 조회**가 중요합니다.  
발송 대상 목록/정책/상태/재시도 횟수 등 다양한 조건으로 데이터를 조회·집계하며,  
특히 **중복 발송 방지**를 위해 트랜잭션(ACID) 기반의 일관성이 필수입니다.

- **ACID 트랜잭션 보장**: 중복 발송 방지에 결정적
- **MVCC 기반 동시성 제어**: 높은 병렬 처리 환경에서 락 경합 완화
- **복잡한 집계/조인 및 실행 제어**에 강점

<img width="425" height="314" alt="image" src="https://github.com/user-attachments/assets/cb044702-db74-4ee9-9493-03a562a5bd2f" />


---

### Snapshot Store: MongoDB Atlas
배치로 생성되는 “청구 스냅샷 데이터”는 월 단위로 대량 적재되고, 생성 이후에는 대부분 **불변(Immutable) + 조회 중심(Read-Only)** 입니다.  
이를 RDB에만 적재하면 정규화/조인 비용으로 조회 부하가 증가할 수 있어,  
본 프로젝트는 스냅샷을 문서(JSON)로 저장해 대량 조회에 유리한 **MongoDB**를 스냅샷 저장소로 채택했습니다.

- 스냅샷 저장(청구서 재완성/재발송용 입력 데이터)
- 발송 실패/재시도 처리 시 스냅샷 재조회로 재렌더 가능

---

### File Storage: Amazon S3
청구서(PDF/HTML/이미지 등)는 대용량 객체 저장에 특화된 S3에 저장하고,  
DB에는 파일 자체가 아닌 **파일 위치(S3 key)** 만 저장합니다.

- DB I/O 및 저장 비용 절감
- 파일 크기 증가에 따른 성능 저하 방지
- Presigned URL(임시 서명 URL)을 통해 발송 시점에만 제한적으로 접근 가능(보안 강화)

---

### Message Broker: Apache Kafka
본 시스템은 “실시간”보다 **안정성과 재처리**가 중요한 구조입니다.  
배치/디스패처에서 생성된 발송 대상 이벤트를 Kafka 토픽에 발행하면,  
발송 서버와 전송 책임 서버가 느슨하게 결합되고 장애 상황에서도 메시지 유실을 방지할 수 있습니다.

- **비동기 이벤트 처리**로 서비스 결합도 감소
- 컨슈머 장애 시에도 Kafka Offset 기반으로 재처리 가능(유실 방지)
- 컨슈머 수평 확장으로 대량 발송 처리량 확보
- Backpressure(외부 발송 API 지연/장애) 완충 효과

---

## Batch 설계 전략

### 초기 접근: 단일 Step(Reader-Processor-Writer)
하나의 Step에서 조회/처리/저장을 모두 수행하는 단일 파이프라인을 고려했습니다.

#### 문제점
월 100만 건 이상 정산을 처리하며, 쿼리가 복잡하고 처리 시간이 길어 장애 가능성이 존재합니다.  
단일 Step은 하나의 트랜잭션으로 묶이기 쉬워 성공 구간/실패 구간 분리가 어렵고,  
재실행 시 **처음부터 다시 실행**해야 할 수 있어 운영 손실이 큽니다.

### 개선 접근: Step 분리 + 상태 저장(CheckPoint) + Chunk
배치 로직을 여러 Step으로 분리하고, Step 경계마다 처리 상태를 저장하여  
실패 시 전체 재실행이 아닌 **체크포인트 기준으로 이어서 처리** 가능하도록 설계했습니다.

---

## Batch Job 구성

### Job1 - 정산 스냅샷 만들기 (결과 저장: MongoDB)
이번 달 사용분을 정산하여 “청구서에 들어갈 데이터” 스냅샷을 생성합니다.

<img width="621" height="582" alt="image-20260113-100255" src="https://github.com/user-attachments/assets/cd1a4f8c-6f61-4705-b907-e8ecf093a39e" />

- **Step0: 정산 기간 고정**
  - 정산 대상 월의 기간(시작/마감) 설정, 정산 시작 상태 변경
  - 이미 완료된 월이면 중복 정산 방지(조기 종료)
- **Step1: 정산 대상 선정**
  - 약 100만 고객 대상 리스트를 선정하여 MongoDB에 저장
- **Step2: 정산**
  - MongoDB에서 대상 조회 → RDB(요금제/결합 등) 조회 → 청구액 산정
  - 산정 결과(청구 항목)를 MongoDB에 스냅샷으로 저장
- **Step3: 상태 완료 처리**
  - 월 단위 정산 완료 상태/시간 기록 후 종료

---

### Job2 - 발송용 파일 만들기 (결과 저장: S3)
Job1의 청구 데이터를 조회하여 Email/SMS 정책에 맞게 청구서를 생성하고, 압축하여 S3에 업로드합니다.

<img width="621" height="582" alt="image-20260113-100255" src="https://github.com/user-attachments/assets/f8a2258f-bafc-44f5-b218-d714338e4d63" />

- **Step0: Job1 결과 확인**
  - 정산 스냅샷 준비 완료 여부 확인(미완료면 종료)
- **Step1: 템플릿 생성에 필요한 데이터 적재**
  - 정책/버전 조회 및 저장 경로 생성(버전별 경로로 섞임 방지)
- **Step2: 정책에 맞는 템플릿 생성 및 메모리 적재**
  - Email/SMS 템플릿을 생성하여 적재
- **Step3: 청구서 생성**
  - MongoDB 스냅샷 조회 → 템플릿 적용 → 결과물 생성 → 압축 → S3 업로드
- **Step4: 업로드 결과 기반 상태 처리**
  - 업로드 성공 시 완료 상태 기록 후 종료

---

## 메시지 발송(Dispatcher/Consumer) 흐름
<img width="6080" height="8192" alt="Untitled diagram-2026-01-13-130557-20260113-130558" src="https://github.com/user-attachments/assets/0267f854-251c-4ae1-b0e7-62e41d3836a1" />

### Dispatcher(2시간 슬롯)
고객의 “수신 거부 시간대(2시간 단위)”를 반영하기 위해, 시스템은 2시간마다 발송 대상을 선별합니다.

- EventBridge Scheduler(2시간 주기)로 Dispatcher Task 실행
- “이번 슬롯에 발송 가능한 고객”만 조회하여 Kafka로 이벤트 발행
- 이벤트에는 대용량 파일 대신 **S3 Key/메타데이터**만 포함

### Consumer(발송 서버)
- Kafka 이벤트 consume
- S3에서 청구서 파일 조회
- 발송(Mock) 수행
- 성공/실패 상태를 PostgreSQL/MongoDB에 업데이트
- 실패 건은 재시도 정책에 따라 재시도 또는 SMS 전환 가능

---

## Infrastructure (AWS)
<img width="1080" height="796" alt="image" src="https://github.com/user-attachments/assets/3cae12e7-77a4-4e84-a73f-9ab34123af9a" />

### 목표
- “월 100만 건 + 2시간 슬롯 발송”을 안정적으로 처리
- Atlas 사용을 위해 **고정 egress IP** 필요
- 비용 최적화를 위해 AWS 서비스 트래픽은 **VPC Endpoint**로 우회하고,
  외부(SaaS: Atlas)만 NAT를 사용

### 인프라 구성 요약 (1AZ)
- **EC2 (Public)**: billing-api (+EIP), Nginx, HTTPS
- **NAT Gateway (Public)**: Private 리소스의 인터넷 egress, Atlas allowlist용 고정 EIP
- **EC2 (Private)**: Kafka(단일 브로커), PostgreSQL, Monitoring(Grafana/Prom/Loki)
- **ECS Fargate (Private)**: consumer service(0~N), dispatcher/settlement task(run-task)
- **VPC Endpoints (Private)**:
  - S3 Gateway Endpoint
  - ECR Interface Endpoint(ecr.api, ecr.dkr)
  - CloudWatch Logs Interface Endpoint
  - (권장) Secrets Manager / SSM / STS Interface Endpoint

### NAT + Endpoint를 함께 쓰는 이유
- **MongoDB Atlas는 VPC Endpoint로 연결 불가(외부 SaaS)**  
  → Private에서 외부로 나가려면 NAT가 필요
- S3/ECR/CloudWatch Logs까지 NAT를 타면 “데이터 처리비”가 불필요하게 증가  
  → AWS 서비스는 Endpoint로 우회하여 NAT 트래픽을 Atlas 중심으로 최소화

---

## Monitoring & Auto Scaling (Kafka Lag 기반)

- Monitoring EC2가 Kafka consumer group lag를 주기적으로 수집
- CloudWatch Custom Metric(`BillingPlatform/Kafka`, `TotalLag`)으로 푸시
- CloudWatch Alarm → ECS Service Auto Scaling(consumer desired 0~N)
- 평시 consumer는 0으로 유지하고, lag가 증가하면 자동으로 scale-out

---


---
