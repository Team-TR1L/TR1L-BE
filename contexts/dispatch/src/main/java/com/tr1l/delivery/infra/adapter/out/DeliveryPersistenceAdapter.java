package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.DeliveryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class DeliveryPersistenceAdapter implements DeliveryRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private static final String TABLE_NAME = "billing_target";

    // PROCESSING -> SENT
    @Override
    public int updateStatusToSent(Long userId, LocalDate billingMonth) {
        // [변경] send_status 컬럼 사용, 복합키(user_id + billing_month) 조건
        String sql = "UPDATE " + TABLE_NAME +
                " SET send_status = 'SENT' " +
                " WHERE user_id = ? AND billing_month = ?";
        return jdbcTemplate.update(sql, userId, billingMonth);
    }
    // 완료 처리 SUCCEED
    public void updateStatusToSucceed(Long userId, LocalDate billingMonth) {
        String sql = "UPDATE " + TABLE_NAME +
                " SET send_status = 'SUCCEED' " +
                " WHERE user_id = ? AND billing_month = ?";
        jdbcTemplate.update(sql, userId, billingMonth);
    }

    // 실패 처리 FAILED
    @Override
    public void updateStatusToFailed(Long userId, LocalDate billingMonth) {
        String sql = "UPDATE " + TABLE_NAME +
                " SET send_status = 'FAILED', attempt_count = attempt_count + 1 " +
                " WHERE user_id = ? AND billing_month = ?";
        jdbcTemplate.update(sql, userId, billingMonth);
    }
}