-- 정산 월 청구 대상 명단 (TR1L-179)
-- 정산 월의 첫 실행 때 Step0 이 청구 대상 사용자를 한 번에 넣는다
-- 재실행은 그 사이 사용자 상태가 바뀌어도 이 명단으로 청구한다
CREATE TABLE IF NOT EXISTS billing_target_member
(
    billing_month date        NOT NULL,
    user_id       bigint      NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_billing_target_member PRIMARY KEY (billing_month, user_id)
);
