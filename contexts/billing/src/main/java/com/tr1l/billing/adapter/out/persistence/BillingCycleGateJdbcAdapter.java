package com.tr1l.billing.adapter.out.persistence;

import com.tr1l.billing.application.port.out.BillingCycleGatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Date;
import java.time.*;

/*==========================
 * GatePort 구현체 - 중복 배치 방지
 *
 * PostgresSQL UPSERT cutoff_at 불변
 *
 * @author [kimdoyeon]
 * @since [생성일자: 26. 1. 17.]
 * @version 1.0
 *==========================*/
@Component
public class BillingCycleGateJdbcAdapter implements BillingCycleGatePort {
    private NamedParameterJdbcTemplate jdbcTemplate;

    public BillingCycleGateJdbcAdapter(@Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate template) {
        this.jdbcTemplate = template;
    }

    @Override
    public GateRow upsertGateAndReturn(YearMonth billingMonth, Instant cutoffAt) {
        LocalDate bm = billingMonth.atDay(1);

        //upsert 쿼리
        String sql = """
                INSERT INTO
                    billing_cycle (billing_month, status, cutoff_at)
                VALUES
                    (:billingMonth, 'RUNNING', :cutoffAt)
                ON CONFLICT (billing_month)
                DO UPDATE SET
                    cutoff_at = billing_cycle.cutoff_at,
                    status = CASE
                                WHEN billing_cycle.status = 'FINISHED' THEN billing_cycle.status
                                ELSE 'RUNNING'
                             END
                RETURNING billing_month, status, cutoff_at
                """;

        OffsetDateTime cutoffOdt = OffsetDateTime.ofInstant(cutoffAt, ZoneOffset.UTC);

        //파라미터 설정
        var parameters = new MapSqlParameterSource()
                .addValue("billingMonth", Date.valueOf(bm))
                .addValue("cutoffAt", cutoffOdt);

        //파라미터 주입 및 쿼리 실행
        return jdbcTemplate.queryForObject(sql, parameters, (rs, rowNum) -> {
            YearMonth yearMonth = YearMonth.from(rs.getDate("billing_month").toLocalDate());
            Instant persistenceCutOff = rs.getObject("cutoff_at", OffsetDateTime.class).toInstant();
            String status = rs.getString("status");

            return new GateRow(yearMonth, persistenceCutOff, status);
        });
    }
}
