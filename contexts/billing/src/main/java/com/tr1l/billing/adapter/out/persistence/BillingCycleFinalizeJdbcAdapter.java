package com.tr1l.billing.adapter.out.persistence;


import com.tr1l.billing.application.port.out.BillingCycleFinalizedJdbcPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/*==========================
 * BillingCycle Finalize JDBC Adapter
 *
 * 역할
 * - 지정된 billingMonth(예: "2026-01")에 대해 billing_cycle을 조회해서
 *   status가 RUNNING인 경우에만 FINISHED로 변경한다.
 *
 * 설계 의도(멱등성)
 * - WHERE 절에 "status = 'RUNNING'" 조건이 있으므로,
 *   이미 FINISHED(또는 다른 상태)인 경우에는 업데이트가 발생하지 않는다(0 rows).
 * - 즉, Step4가 재실행/중복 실행되어도 FINISHED로 “또 찍는” 문제가 생기지 않는다.
 *
 * 전제
 * - billing_cycle.billing_month 컬럼은 "YYYY-MM" 형태의 문자열(TEXT/VARCHAR)로 저장되어 있어야 한다.
 *   (DATE로 저장하는 구조라면 billingMonth.toString() 바인딩이 아니라 Date로 바인딩해야 함)
 *
 * 사용하는 DB
 * - @Qualifier("targetNamedJdbcTemplate")를 통해 target Postgres에 붙는다.
 *
 * 반환값
 * - 1: 이번 호출에서 RUNNING -> FINISHED로 변경됨
 * - 0: 이미 FINISHED거나 RUNNING이 아니어서 변경 없음(NOOP)
 *
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
    public int markFinishedIfRunning(YearMonth billingMonth) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("billingMonth", billingMonth.toString()); // "YYYY-MM"

        String sql = """
                UPDATE billing_cycle
                SET status = 'FINISHED'
                WHERE billing_month = :billingMonth
                  AND status = 'RUNNING'
                """;

        return jdbcTemplate.update(sql, params);
    }
}
