CREATE TABLE IF NOT EXISTS billing_targets
(
    billing_month            date           NOT NULL,
    user_id                  bigint         NOT NULL,
    created_at               timestamp      NOT NULL DEFAULT now(),
    user_name                varchar(100)   NOT NULL,
    user_birth_date          date           NOT NULL,
    recipient_email          varchar(150)   NOT NULL,
    recipient_phone          varchar(150)   NOT NULL,
    plan_name                varchar(100)   NOT NULL,
    plan_monthly_price       bigint         NOT NULL,
    network_type_name        varchar(10)    NOT NULL,
    data_billing_type_code   varchar(10)    NOT NULL,
    data_billing_type_name   varchar(50)    NOT NULL,
    included_data_mb         bigint         NOT NULL,
    excess_charge_per_mb     numeric(12, 6) NOT NULL,
    used_data_mb             bigint         NOT NULL,
    has_contract             boolean        NOT NULL default false,
    contract_rate            numeric(6, 5)  NOT NULL,
    contract_duration_months integer        NOT NULL,
    soldier_eligible         boolean        NOT NULL default false,
    welfare_eligible         boolean        NOT NULL default false,
    welfare_code             varchar(20),
    welfare_name             varchar(50)    NOT NULL,
    welfare_rate             numeric(6, 5)  NOT NULL,
    welfare_cap_amount       bigint         NOT NULL default 0,
    from_time                varchar(2)     NULL,
    to_time                  varchar(2)     NULL,
    day_time                 varchar(2)     NULL,
    attempt_count            int                     default 0,
    send_status              varchar(50)    NOT NULL default 'INIT',
    s3_url_jsonb             jsonb          NULL     default '[]'::jsonb,
    send_option_jsonb        jsonb          NULL     default '[]'::jsonb,
    options_jsonb            jsonb          NULL     default '[]'::jsonb,

    CONSTRAINT pk_billing_targets PRIMARY KEY (billing_month, user_id)
);

CREATE INDEX IF NOT EXISTS idx_billing_targets_month_user
    ON billing_targets (billing_month, user_id);
