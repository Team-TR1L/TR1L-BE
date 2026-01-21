CREATE TABLE IF NOT EXISTS billing_format_run (
  billing_month date NOT NULL,
  policy_version varchar(50) NOT NULL,
  policy_order varchar(100) NOT NULL,
  policy_index int NOT NULL,
  channel_type varchar(20) NOT NULL,

  status varchar(20) NOT NULL,

  started_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT pk_billing_format_run
    PRIMARY KEY (billing_month, policy_version, policy_index)
);

-- (선택) 월 단위 조회가 잦으면 추가
CREATE INDEX IF NOT EXISTS idx_billing_format_run_month
  ON billing_format_run (billing_month);