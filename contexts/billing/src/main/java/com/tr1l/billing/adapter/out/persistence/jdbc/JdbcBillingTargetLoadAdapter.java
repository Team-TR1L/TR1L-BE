package com.tr1l.billing.adapter.out.persistence.jdbc;

import com.tr1l.billing.application.dto.BillingTargetRow;
import com.tr1l.billing.application.port.out.BillingTargetLoadPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Component
public class JdbcBillingTargetLoadAdapter implements BillingTargetLoadPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBillingTargetLoadAdapter(@Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, BillingTargetRow> loadByUserIds(YearMonth billingMonth, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();

        String sql = """
                SELECT
                  to_char(billing_month, 'YYYY-MM') AS billing_month,
                  user_id,
                  user_name,
                  user_birth_date,
                  recipient_email_enc,
                  recipient_phone_enc,
                  plan_name,
                  plan_monthly_price,
                  network_type_name,
                  data_billing_type_code,
                  data_billing_type_name,
                  included_data_mb,
                  excess_charge_per_mb,
                  used_data_mb,
                  has_contract,
                  contract_rate,
                  contract_duration_months,
                  soldier_eligible,
                  welfare_eligible,
                  welfare_code,
                  welfare_name,
                  welfare_rate,
                  welfare_cap_amount,
                  options_jsonb
                FROM billing_targets_mv
                WHERE billing_month = :bm
                  AND user_id IN (:userIds)
                """;

        var params = Map.of(
                "bm", Date.valueOf(billingMonth.atDay(1)),
                "userIds", userIds
        );

        List<BillingTargetRow> rows = jdbc.query(sql, params, (rs, n) -> new BillingTargetRow(
                rs.getString("billing_month"), //"YYYY-MM" 형식임
                rs.getString("user_name"),
                rs.getLong("user_id"),
                rs.getString("user_birth_date"),
                rs.getString("recipient_email"),
                rs.getString("recipient_phone"),

                rs.getString("plan_name"),
                rs.getLong("plan_monthly_price"),
                rs.getString("network_type_name"),
                rs.getString("data_billing_type_code"),
                rs.getString("data_billing_type_name"),
                (Long) rs.getObject("included_data_mb"),
                rs.getBigDecimal("excess_charge_per_mb"),
                rs.getLong("used_data_mb"),

                rs.getBoolean("has_contract"),
                rs.getDouble("contract_rate"),
                (Integer) rs.getObject("contract_duration_months"),

                rs.getBoolean("soldier_eligible"),

                rs.getBoolean("welfare_eligible"),
                rs.getString("welfare_code"),
                rs.getString("welfare_name"),
                rs.getDouble("welfare_rate"),
                (Long) rs.getObject("welfare_cap_amount"),
                rs.getString("options_jsonb")
        ));

        return rows.stream().collect(Collectors.toMap(
                BillingTargetRow::userId,
                r -> r,
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }
}