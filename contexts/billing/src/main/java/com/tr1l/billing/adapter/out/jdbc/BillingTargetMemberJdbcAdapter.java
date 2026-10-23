package com.tr1l.billing.adapter.out.jdbc;

import com.tr1l.billing.application.port.out.BillingTargetMemberPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*==========================
 * 정산 월 청구 대상 명단
 *
 * - 청구 대상 조건은 Step1 기준 조회(01_select_base_targets.sql)와 같다
 * - main DB 에서 읽고 target DB billing_target_member 에 넣는다
 * - 호출한 쪽의 target 트랜잭션 안에서 넣어서 명단은 전부 들어가거나 하나도 안 들어간다
 *==========================*/
@Component
public class BillingTargetMemberJdbcAdapter implements BillingTargetMemberPort {
    private static final int INSERT_CHUNK = 50_000;

    private final NamedParameterJdbcTemplate mainJdbc;
    private final NamedParameterJdbcTemplate targetJdbc;

    public BillingTargetMemberJdbcAdapter(
            @Qualifier("mainNamedJdbcTemplate") NamedParameterJdbcTemplate mainJdbc,
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate targetJdbc
    ) {
        this.mainJdbc = mainJdbc;
        this.targetJdbc = targetJdbc;
    }

    @Override
    public int freezeIfAbsent(YearMonth billingMonth) {
        MapSqlParameterSource month = new MapSqlParameterSource("billingMonth", Date.valueOf(billingMonth.atDay(1)));
        Boolean frozen = targetJdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM billing_target_member WHERE billing_month = :billingMonth)",
                month, Boolean.class);
        if (Boolean.TRUE.equals(frozen)) {
            return 0;
        }

        List<Long> userIds = mainJdbc.getJdbcTemplate().queryForList("""
                SELECT u.user_id
                FROM users u
                WHERE u.user_role = 'USER'
                  AND u.user_status = 'ACTIVE'
                  AND u.plan_code IS NOT NULL
                ORDER BY u.user_id
                """, Long.class);

        int inserted = 0;
        for (int from = 0; from < userIds.size(); from += INSERT_CHUNK) {
            List<Long> chunk = userIds.subList(from, Math.min(from + INSERT_CHUNK, userIds.size()));
            inserted += targetJdbc.getJdbcTemplate().update("""
                    INSERT INTO billing_target_member (billing_month, user_id)
                    SELECT ?, unnest(?::bigint[])
                    ON CONFLICT DO NOTHING
                    """, ps -> {
                ps.setDate(1, Date.valueOf(billingMonth.atDay(1)));
                ps.setArray(2, ps.getConnection().createArrayOf("bigint", chunk.toArray()));
            });
        }
        return inserted;
    }

    @Override
    public Set<Long> membersAmong(YearMonth billingMonth, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Set.of();

        return new HashSet<>(targetJdbc.queryForList("""
                SELECT user_id
                FROM billing_target_member
                WHERE billing_month = :billingMonth
                  AND user_id IN (:userIds)
                """, new MapSqlParameterSource()
                .addValue("billingMonth", Date.valueOf(billingMonth.atDay(1)))
                .addValue("userIds", userIds), Long.class));
    }
}
