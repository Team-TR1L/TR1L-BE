package com.tr1l.worker.batch.formatjob.adapter.out.persistence;

import com.tr1l.worker.batch.formatjob.port.out.FormatGatePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static com.tr1l.worker.batch.formatjob.port.out.FormatGatePort.FormatGateResult.Decision.*;

@Component

public class FormatGateJdbcAdapter implements FormatGatePort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FormatGateJdbcAdapter(@Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String findBillingCycleStatus(LocalDate billingMonth) {
        final String sql = """
                SELECT status
                FROM billing_cycle
                WHERE billing_month= :billingMonth
                """;
        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("billingMonth", billingMonth),
                rs -> rs.next() ? rs.getString("status") : null);
    }

    @Override
    public FormatGateResult tryStartFormatRun(LocalDate billingMonth,
                                              String policyVersion,
                                              String policyOrder,
                                              int policyIndex,
                                              String channelType) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("billingMonth", billingMonth)
                .addValue("policyVersion", policyVersion)
                .addValue("policyOrder", policyOrder)
                .addValue("policyIndex", policyIndex)
                .addValue("channelType", channelType);

        // 1) 선점 시도: 동일 (billingMonth, policyVersion, policyIndex) 키로 최초 1회만 insert 성공
        final String insertSql = """
            INSERT INTO billing_format_run(
              billing_month, policy_version, policy_order, policy_index, channel_type, status
            )
            VALUES (
              :billingMonth, :policyVersion, :policyOrder, :policyIndex, :channelType, 'RUNNING'
            )
            ON CONFLICT (billing_month, policy_version, policy_index)
            DO NOTHING
        """;

        int inserted = jdbcTemplate.update(insertSql, params);
        if (inserted == 1) {
            // 내가 선점 성공 → 이번 실행이 RUNNING으로 고정됨
            return new FormatGateResult(STARTED, "RUNNING", policyOrder, channelType);
        }

        // 2) 이미 존재하는 run → 상태/파라미터를 읽어서 분기
        final String selectSql = """
            SELECT status, policy_order, channel_type
            FROM billing_format_run
            WHERE billing_month  = :billingMonth
              AND policy_version = :policyVersion
              AND policy_index   = :policyIndex
        """;

        try {
            return jdbcTemplate.query(selectSql, params, rs -> {
                if (!rs.next()) {
                    // insert는 실패했는데 row가 없다? 일반적으로는 거의 불가능(동시성/트랜잭션 꼬임)
                    // 운영상은 즉시 실패시키는 게 안전하다.
                    throw new IllegalStateException("format_run row not found after conflict. key="
                            + billingMonth + "/" + policyVersion + "/" + policyIndex);
                }

                String storedStatus = rs.getString("status");
                String storedOrder = rs.getString("policy_order");
                String storedChannel = rs.getString("channel_type");

                // ---- 파라미터 불일치 방어 ----
                // 같은 run 키로 "다른 정책/채널"을 덮어씌우려는 시도는 사고로 이어지므로 Fail fast 권장
                boolean sameParams = safeEq(storedOrder, policyOrder) && safeEq(storedChannel, channelType);
                if (!sameParams) {
                    return new FormatGateResult(FAIL_PARAM_MISMATCH, storedStatus, storedOrder, storedChannel);
                }

                // ---- 상태 기반 분기 ----
                // DONE이면 멱등 NOOP
                if ("DONE".equalsIgnoreCase(storedStatus)) {
                    return new FormatGateResult(NOOP_ALREADY_DONE, storedStatus, storedOrder, storedChannel);
                }

                // RUNNING이면 중복 실행이므로 NOOP
                if ("RUNNING".equalsIgnoreCase(storedStatus)) {
                    return new FormatGateResult(NOOP_ALREADY_RUNNING, storedStatus, storedOrder, storedChannel);
                }

                // FAILED/PARTIAL 등: (정책) 동일 파라미터면 RUNNING으로 재전환 후 재시작 허용
                // 이 부분이 싫으면 "NOOP_ALREADY_RUNNING" 또는 "NOOP"로 바꿔도 됨.
                final String restartSql = """
                    UPDATE billing_format_run
                    SET status = 'RUNNING',
                        updated_at = now()
                    WHERE billing_month  = :billingMonth
                      AND policy_version = :policyVersion
                      AND policy_index   = :policyIndex
                      AND status <> 'RUNNING'
                """;
                jdbcTemplate.update(restartSql, params);

                return new FormatGateResult(STARTED, "RUNNING", storedOrder, storedChannel);
            });
        } catch (DataAccessException e) {
            // JDBC/DB 관련 예외는 상위로 올려서 Step 실패로 처리
            throw e;
        }
    }

    private boolean safeEq(String a, String b) {
        return java.util.Objects.equals(a, b);
    }
}
