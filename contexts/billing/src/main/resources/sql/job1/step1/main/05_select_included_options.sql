-- 유저가 가입한 부가 서비스 코드/이름/가격 조회
-- [ parameter ]
-- userIds : 유효한 유저 아이디
-- endDate : 정산 기준일 마지막 날

SELECT
  uos.user_id,
  os.option_service_code,
  os.option_service_name,
  COALESCE(os.monthly_price, 0) AS monthly_price

FROM temp_user_ids AS t
JOIN user_option_subscription AS uos
  ON t.user_id = uos.user_id
JOIN option_service os
  ON os.option_service_code = uos.option_service_code

WHERE uos.start_date < (:endDate::date + INTERVAL '1 day')
  AND uos.end_date   > :startDate::date
-- 사용자별 옵션 순서가 청구서 옵션 라인 순서가 된다. 순서를 정하지 않으면 테이블이 클 때
-- PostgreSQL synchronized seqscan 이 중간부터 읽어 같은 입력에도 순서가 바뀐다 (TR1L-178)
ORDER BY uos.user_id, uos.user_option_id;