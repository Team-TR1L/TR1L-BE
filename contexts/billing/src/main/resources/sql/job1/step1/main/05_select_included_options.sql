-- 유저가 가입한 부가 서비스 코드/이름/가격 조회
-- [ parameter ]
-- userIds : 유효한 유저 아이디
-- endDate : 정산 기준일 마지막 날

SELECT
  uos.user_id,
  os.option_service_code,
  os.option_service_name,
  COALESCE(os.monthly_price, 0) AS monthly_price

FROM user_option_subscription uos
JOIN option_service os
  ON os.option_service_code = uos.option_service_code

WHERE uos.user_id IN (:userIds)
  AND uos.start_date < :endDate::date
  AND uos.end_date   > :startDate::date;
