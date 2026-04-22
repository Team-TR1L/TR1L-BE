package com.tr1l.worker.reliability.invariant;

import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// postgres sql 체크 실행기
public final class PostgresInvariantChecks {
    private static final int MAX_SAMPLES = 10;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ReliabilityResourceCatalog catalog;

    public PostgresInvariantChecks(
            NamedParameterJdbcTemplate jdbcTemplate,
            ReliabilityResourceCatalog catalog
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.catalog = catalog;
    }

    public InvariantResult execute(InvariantDefinition definition, LocalDate billingMonthDay) {
        String sql = catalog.loadCheckText(definition.checkRef());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource("billingMonthDay", billingMonthDay)
        );

        return new InvariantResult(
                definition.id(),
                definition.title(),
                definition.scope(),
                definition.checkType(),
                rows.size() == definition.expectedViolationCount(),
                rows.size(),
                rows.stream().limit(MAX_SAMPLES).toList()
        );
    }
}
