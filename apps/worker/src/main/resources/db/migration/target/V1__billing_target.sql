-- step1 step2 중간 테이블 -> 데이터 평탄화 테이블
CREATE TABLE IF NOT EXISTS billing_targets
(
    billing_month date NOT NULL ,
    user_id bigint NOT NULL ,--
    created_at timestamp NOT NULL DEFAULT now(),--

    user_name varchar(10) NOT NULL ,--
    user_birth_date date NOT NULL ,--
    recipient_email varchar(100) NOT NULL ,--
    recipient_phone varchar(100) NOT NULL ,--

    --요금제 이름
    plan_name varchar(100) NOT NULL,--
    -- 요금제 가격
    plan_monthly_price bigint NOT NULL ,--
    --요금제 네트워크 유형
    network_type_name varchar(10) NOT NULL ,--
    --데이터 과금 유형 코드
    data_billing_type_code varchar(10) NOT NULL ,--
    --데이터 과금 유형 이름
    data_billing_type_name varchar(50) NOT NULL ,--
    --요금제에 포함된 데이터 사용량
    included_data_mb bigint NOT NULL ,--
    --추가 데이터 사용량에 대한 과금
    excess_charge_per_mb numeric(12,6) NOT NULL ,--
    --총 데이터 사용량
    used_data_mb bigint NOT NULL ,
    --선택 약정 유무
    has_contract boolean NOT NULL default false,
    --선택 약정 할인율
    contract_rate numeric(6,5) NOT NULL ,
    --선택 약정 월자
    contract_duration_months integer NOT NULL ,
    --군인 유무
    soldier_eligible boolean NOT NULL default false,
    --복지 유무
    welfare_eligible boolean NOT NULL default false,
    --복지 유형 코드
    welfare_code varchar(20) ,
    --복지 유형 이름
    welfare_name varchar(50) NOT NULL ,
    --복지 할인율
    welfare_rate numeric(6,5) NOT NULL ,
    --복지 상한선
    welfare_cap_amount bigint NOT NULL default 0,
    --부가 서비스
    options_jsonb jsonb NOT NULL default '[]'::jsonb,

    --===== 제약 조건 =======
    CONSTRAINT pk_billing_targets PRIMARY KEY (billing_month,user_id)
);

-- 청구서 월 + userId 인덱스 처리
CREATE INDEX IF NOT EXISTS idx_billing_targets_month_user
    ON billing_targets (billing_month,user_id);

