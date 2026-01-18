package com.tr1l.billing.adapter.out.jdbc;

import com.tr1l.billing.application.model.BillingTargetFlatRow;
import com.tr1l.billing.application.port.out.BillingTargetSinkPort;
import com.tr1l.util.SqlResourceReader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BillingTargetsUpsertJdbcAdapter implements BillingTargetSinkPort {
    private final NamedParameterJdbcTemplate targetJdbc;
    private final SqlResourceReader sqlReader;

    public BillingTargetsUpsertJdbcAdapter(
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate targetJdbc,
            SqlResourceReader sqlReader
    ){
        this.targetJdbc=targetJdbc;
        this.sqlReader=sqlReader;
    }

    @Value("${app.sql.step1.targetBase}/01_upsert_base_targets.sql")
    private Resource upsertSqlResource;

    @Override
    public void upsertBillingTargets(List<BillingTargetFlatRow> rows) {
        if (rows==null || rows.isEmpty()) return;

        String sql=sqlReader.read(upsertSqlResource);

        targetJdbc.batchUpdate(sql, SqlParameterSourceUtils.createBatch(rows.toArray()));
    }
}
