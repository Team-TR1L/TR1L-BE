-- 2026 01 work 정리
-- rerun 전 target 상태 초기화 목적
DELETE FROM billing_work
WHERE billing_month_day = DATE '2026-01-01';

-- 2026 01 targets 정리
DELETE FROM billing_targets
WHERE billing_month = DATE '2026-01-01';

-- 2026 01 cycle 정리
DELETE FROM billing_cycle
WHERE billing_month = DATE '2026-01-01';
