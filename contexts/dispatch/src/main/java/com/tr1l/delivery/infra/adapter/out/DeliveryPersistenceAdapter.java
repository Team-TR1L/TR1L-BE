package com.tr1l.delivery.infra.adapter.out;

import com.tr1l.delivery.application.port.out.DeliveryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeliveryPersistenceAdapter implements DeliveryRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private static final String TABLE_NAME = "dispatch_message";

    // PROCESSING -> SENT
    @Override
    public int updateStatusToSent(Long userId) {
        // 오직 PROCESSING 상태일 때만 SENT로 변경 가능
        String sql = "UPDATE " + TABLE_NAME + " SET status = 'SENT' WHERE user_id = ?";
        return jdbcTemplate.update(sql, userId);
    }

    // 완료 처리 SUCCEED
    @Override
    public void updateStatusToSucceed(Long userId) {
        String sql = "UPDATE " + TABLE_NAME + " SET status = 'SUCCEED' WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }

    // 실패 처리 FAILED
    @Override
    public void updateStatusToFailed(Long userId) {
        String sql = "UPDATE " + TABLE_NAME +
                " SET status = 'FAILED', attempt_count = attempt_count + 1 " +
                " WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
}