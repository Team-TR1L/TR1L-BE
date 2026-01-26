package com.tr1l.billing.adapter.out.persistence.jdbc;

import com.tr1l.billing.application.port.out.WorkDocStatusPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class WorkDocStatusAdapter implements WorkDocStatusPort {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WorkDocStatusAdapter(
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }


    @Override
    public void markCalculatedAll(List<CalculatedUpdate> updates, Instant now) {
        if (updates == null || updates.isEmpty()) return;

        Timestamp ts = Timestamp.from(now);

        MapSqlParameterSource[] batch = updates.stream()
                .map(u -> new MapSqlParameterSource()
                        .addValue("billingMonthDay", parseMonthDay(u.workId()))
                        .addValue("userId", parseUserId(u.workId()))
                        .addValue("billingId", u.billingId())
                        .addValue("now", ts))
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(BillingWorkSQL.MARK_CALCULATED, batch);
    }

    @Override
    public void markFailedAll(List<FailedUpdate> updates, Instant now) {
        if (updates == null || updates.isEmpty()) return;

        Timestamp ts = Timestamp.from(now);

        MapSqlParameterSource[] batch = updates.stream()
                .map(u -> new MapSqlParameterSource()
                        .addValue("billingMonthDay", parseMonthDay(u.workId()))
                        .addValue("userId", parseUserId(u.workId()))
                        .addValue("errorCode", u.errorCode())
                        .addValue("errorMessage", u.errorMessage())
                        .addValue("now", ts))
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(BillingWorkSQL.MARK_FAILED, batch);
    }

    // workId = "YYYY-MM-DD:userId"
    private static LocalDate parseMonthDay(String workId) {
        int idx = workId.indexOf(':');
        if (idx <= 0) throw new IllegalArgumentException("Invalid workId format: " + workId);
        return LocalDate.parse(workId.substring(0, idx));
    }

    private static long parseUserId(String workId) {
        int idx = workId.indexOf(':');
        if (idx <= 0 || idx == workId.length() - 1)
            throw new IllegalArgumentException("Invalid workId format: " + workId);
        return Long.parseLong(workId.substring(idx + 1));
    }
}
