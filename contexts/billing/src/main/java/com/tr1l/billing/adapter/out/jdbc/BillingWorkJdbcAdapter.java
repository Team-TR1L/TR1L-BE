package com.tr1l.billing.adapter.out.jdbc;

import com.tr1l.billing.application.command.BillingWorkEnqueueCommand;
import com.tr1l.billing.application.port.out.BillingWorkUpsertPort;
import com.tr1l.util.SqlResourceReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class BillingWorkJdbcAdapter implements BillingWorkUpsertPort {
    private final String sql;
    private NamedParameterJdbcTemplate jdbcTemplate;


    public BillingWorkJdbcAdapter (
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            SqlResourceReader sqlResourceReader,
            @Value("${app.sql.step2.targetBase}/01_upsert_billing_work.sql") Resource baseResource
    ) {
        this.jdbcTemplate=jdbcTemplate;
        this.sql=sqlResourceReader.read(baseResource);
    }

    @Override
    public void bulkUpsertOnInsert(List<BillingWorkEnqueueCommand> keys, Instant now) {
        //chunk 내 중복 제거
        Map<String,BillingWorkEnqueueCommand> unique = new LinkedHashMap<>(keys.size());

        for (BillingWorkEnqueueCommand key:keys){
            if (key==null) continue;
            unique.put(key.billingYearMonth()+":"+key.userId(),key);
        }

        if (unique.isEmpty()) return;

        Timestamp ts = Timestamp.from(now);

        MapSqlParameterSource[] batch = unique.values().stream()
                .map(k -> new MapSqlParameterSource()
                        .addValue("billingMonthDay", k.billingYearMonth())
                        .addValue("userId", k.userId())
                        .addValue("now", ts))
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql,batch);
    }
}
