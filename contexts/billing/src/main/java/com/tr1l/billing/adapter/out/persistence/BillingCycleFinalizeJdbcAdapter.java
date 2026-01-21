package com.tr1l.billing.adapter.out.persistence;


import com.tr1l.billing.application.port.out.BillingCycleFinalizedJdbcPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/*==========================
 * BillingCycle Finalize JDBC Adapter
 *
 * - billing_cycle.billing_month 가 DATE(월의 1일로 저장: YYYY-MM-01)인 전제
 * - status가 RUNNING일 때만 FINISHED로 변경 (멱등)

 * @author nonstop
 * @since 2026. 1. 18.
 * @version 1.0.0
 *==========================*/
@Component
public class BillingCycleFinalizeJdbcAdapter implements BillingCycleFinalizedJdbcPort {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingCycleFinalizeJdbcAdapter(
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int markFinishedIfRunning(LocalDate billingMonthDay) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("billingMonth", java.sql.Date.valueOf(billingMonthDay));
        String sql = """
        UPDATE billing_cycle
        SET status = 'FINISHED'
        WHERE billing_month = :billingMonth
          AND status = 'RUNNING'
        """;

        return jdbcTemplate.update(sql, params);
    }
}
