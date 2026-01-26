INSERT INTO billing_work
(billing_month_day, user_id, status, attempt_count, created_at, updated_at)
VALUES (:billingMonthDay, :userId, 'TARGET', 0, :now, :now)
ON CONFLICT (billing_month_day, user_id) DO NOTHING