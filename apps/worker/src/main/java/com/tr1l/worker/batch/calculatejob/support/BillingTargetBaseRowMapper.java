package com.tr1l.worker.batch.calculatejob.support;

import com.tr1l.billing.application.model.BillingTargetBaseRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
@Component
public class BillingTargetBaseRowMapper implements RowMapper<BillingTargetBaseRow> {
    /**
     * Implementations must implement this method to map each row of data in the
     * {@code ResultSet}. This method should not call {@code next()} on the
     * {@code ResultSet}; it is only supposed to map values of the current row.
     *
     * @param rs     the {@code ResultSet} to map (pre-initialized for the current row)
     * @param rowNum the number of the current row
     * @return the result object for the current row (may be {@code null})
     * @throws SQLException if an SQLException is encountered while getting
     *                      column values (that is, there's no need to catch SQLException)
     */
    @Override
    public BillingTargetBaseRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BillingTargetBaseRow(
                rs.getLong("user_id"),
                rs.getString("user_name"),
                rs.getDate("user_birth_date").toLocalDate(),
                rs.getString("recipient_phone"),
                rs.getString("recipient_email"),

                rs.getString("plan_name"),
                rs.getLong("plan_monthly_price"),
                rs.getString("network_type_name"),

                rs.getString("data_billing_type_code"),
                rs.getString("data_billing_type_name"),
                rs.getLong("included_data_mb"),
                rs.getBigDecimal("excess_charge_per_mb"),

                rs.getString("welfare_code"),
                rs.getString("welfare_name"),
                rs.getBigDecimal("welfare_rate"),
                rs.getLong("welfare_cap_amount")
        );
    }
}
