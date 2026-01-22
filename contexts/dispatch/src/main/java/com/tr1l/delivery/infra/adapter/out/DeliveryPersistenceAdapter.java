package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.DeliveryRepositoryPort;
import com.tr1l.dispatch.application.exception.DispatchDomainException;
import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class DeliveryPersistenceAdapter implements DeliveryRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private static final String TABLE_NAME = "billing_targets";

    // PROCESSING -> SENT
    @Override
    public int updateStatusToSent(Long userId, LocalDate billingMonth) {
        try{
            // send_status 컬럼 사용, 복합키(user_id + billing_month) 조건
            String sql = "UPDATE " + TABLE_NAME +
                    " SET send_status = 'SENT' " +
                    " WHERE user_id = ? AND billing_month = ?";
            return jdbcTemplate.update(sql, userId, billingMonth);
        } catch (DataAccessException e){
            // SENT로 업데이트 하다가 오류 -> 카프카 리트라이 토픽
            throw new DispatchDomainException(DispatchErrorCode.DB_UPDATE_FAILED, e);
        }
    }
    // 완료 처리 SUCCEED
    @Override
    public void updateStatusToSucceed(Long userId, LocalDate billingMonth) {
        try{
            String sql = "UPDATE " + TABLE_NAME +
                    " SET send_status = 'SUCCEED' " +
                    " WHERE user_id = ? AND billing_month = ?";
            jdbcTemplate.update(sql, userId, billingMonth);
        } catch (DataAccessException e){
            throw new DispatchDomainException(DispatchErrorCode.DB_UPDATE_FAILED, e);
        }
    }

    // 실패 처리 FAILED
    @Override
    public void updateStatusToFailed(Long userId, LocalDate billingMonth) {
        try{
            String sql = "UPDATE " + TABLE_NAME +
                    " SET send_status = 'FAILED', attempt_count = attempt_count + 1 " +
                    " WHERE user_id = ? AND billing_month = ?";
            jdbcTemplate.update(sql, userId, billingMonth);
        } catch (DataAccessException e){
            throw new DispatchDomainException(DispatchErrorCode.DB_UPDATE_FAILED, e);
        }
    }
}