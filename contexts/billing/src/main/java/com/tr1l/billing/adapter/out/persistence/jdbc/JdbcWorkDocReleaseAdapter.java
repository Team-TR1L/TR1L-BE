package com.tr1l.billing.adapter.out.persistence.jdbc;

import com.tr1l.billing.application.port.out.WorkDocReleasePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class JdbcWorkDocReleaseAdapter implements WorkDocReleasePort {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkDocReleaseAdapter(
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int releaseClaimed(LocalDate billingMonthDay, String workerId, Instant now) {
        if (billingMonthDay == null || workerId == null || workerId.isBlank()) return 0;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("billingMonthDay", Date.valueOf(billingMonthDay))
                .addValue("workerId", workerId)
                .addValue("now", Timestamp.from(now));

        return jdbcTemplate.update(BillingWorkSQL.RELEASE_CLAIMED, params);
    }
}
