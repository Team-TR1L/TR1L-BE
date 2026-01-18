-- 유효한 군인 레코드 추출 (당월에 1일이라도 군인이면 할인)
-- [ parameter ]
-- usrIds : 유효한 유저의 아이디들
-- billingMonth : 정산 기준 월

SELECT uss.user_id
FROM user_soldier AS uss
JOIN users AS u ON u.user_id = uss.user_id
JOIN plan AS p ON p.plan_code = u.plan_code
WHERE uss.user_id IN (:userIds)
  AND p.is_military_eligible = true -- 유저의 요금제가 군인 할인이 되는 경우

  AND uss.start_date < (:startDate::date + INTERVAL '1 month')::date
  AND (uss.end_date IS NULL OR uss.end_date >= :startDate::date);