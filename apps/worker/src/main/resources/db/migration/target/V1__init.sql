CREATE TABLE public.billing_cycle
(
    billing_month DATE PRIMARY KEY,
    status varchar(10) NOT NULL,
    cutoff_at timestamp(6) with time zone NOT NULL UNIQUE,

    CONSTRAINT uk_month_status
        UNIQUE (billing_month,status)
);