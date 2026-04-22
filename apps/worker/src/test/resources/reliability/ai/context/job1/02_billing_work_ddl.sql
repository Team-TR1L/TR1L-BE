CREATE TABLE IF NOT EXISTS billing_work
(
    user_id           BIGINT      NOT NULL,
    billing_month_day DATE        NOT NULL,
    billing_id        varchar(64),
    error_code        varchar(64),
    error_message     text,
    claimed_by        varchar(64),
    lease_until       timestamptz NULL,
    last_error        text        NULL,
    status            varchar(30) NOT NULL,
    attempt_count     INTEGER     NOT NULL DEFAULT 0,
    created_at        timestamp   NOT NULL DEFAULT now(),
    updated_at        timestamp   NOT NULL DEFAULT now(),

    PRIMARY KEY (billing_month_day, user_id),

    CONSTRAINT ck_billing_work_status
        CHECK (status IN ('TARGET', 'PROCESSING', 'CALCULATED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_billing_work_month_status
    ON billing_work (billing_month_day, status, user_id);
