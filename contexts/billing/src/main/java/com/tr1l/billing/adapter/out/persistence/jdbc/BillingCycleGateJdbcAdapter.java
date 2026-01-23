package com.tr1l.billing.adapter.out.persistence.jdbc;

import com.tr1l.billing.application.port.out.BillingCycleGatePort;
import com.tr1l.util.SqlResourceReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
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
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SqlResourceReader resourceReader;
    private final Resource upsertGateSql;

    public BillingCycleGateJdbcAdapter(
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate template,
            SqlResourceReader resourceReader,
            @Value("${app.sql.step0.targetBase}/insert_billing_cycle.sql")Resource upsertGateSql
            ) {
        this.resourceReader=resourceReader;
        this.jdbcTemplate = template;
        this.upsertGateSql=upsertGateSql;
    }

    @Override
    public GateRow upsertGateAndReturn(YearMonth billingMonth, Instant cutoffAt) {
        LocalDate bm = billingMonth.atDay(1);
        OffsetDateTime cutoffOdt = OffsetDateTime.ofInstant(cutoffAt, ZoneOffset.UTC);

        //UPSERT Query
        String query = resourceReader.read(upsertGateSql);

        //파라미터 설정
        var parameters = new MapSqlParameterSource()
                .addValue("billingMonth", Date.valueOf(bm))
                .addValue("cutoffAt", cutoffOdt);

        //파라미터 주입 및 쿼리 실행
        return jdbcTemplate.queryForObject(query, parameters, (rs, rowNum) -> {
            YearMonth yearMonth = YearMonth.from(rs.getDate("billing_month").toLocalDate());
            Instant persistenceCutOff = rs.getObject("cutoff_at", OffsetDateTime.class).toInstant();
            String status = rs.getString("status");

            return new GateRow(yearMonth, persistenceCutOff, status);
        });
    }
}
