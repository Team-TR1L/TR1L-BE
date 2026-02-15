package com.tr1l.billing.adapter.out.persistence.jdbc;

import com.tr1l.billing.application.port.out.WorkDocRangePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * billing_work
 */
@Component
public class JdbcWorkDocRangeAdapter implements WorkDocRangePort {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcWorkDocRangeAdapter(
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Override
    public UserIdRange findUserIdRange(YearMonth billingMonth) {
        LocalDate billingMonthDay = billingMonth.atDay(1);
        String sql = """
            SELECT MIN(user_id) AS min_user_id,
                   MAX(user_id) AS max_user_id
            FROM billing_work
            WHERE billing_month_day = :billingMonthDay
              AND status IN ('TARGET', 'PROCESSING')
            """;

        Map<String, Object> params = Map.of(
                "billingMonthDay", Date.valueOf(billingMonthDay)
        );

        return jdbc.query(sql, params, rs -> {
            if (!rs.next()) return null;
            Long min = (Long) rs.getObject("min_user_id");
            Long max = (Long) rs.getObject("max_user_id");
            if (min == null || max == null) return null;
            return new UserIdRange(min, max);
        });
    }
}
