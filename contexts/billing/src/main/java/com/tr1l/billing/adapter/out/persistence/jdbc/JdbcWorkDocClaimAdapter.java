package com.tr1l.billing.adapter.out.persistence.jdbc;

import com.tr1l.billing.application.port.out.WorkDocClaimPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * RDB billing_work에서 TARGET/만료된 PROCESSING을 선점한다.
 */
@Slf4j
@Component
@Primary
public class JdbcWorkDocClaimAdapter implements WorkDocClaimPort {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcWorkDocClaimAdapter(
            @Qualifier("targetNamedJdbcTemplate") NamedParameterJdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ClaimedWorkDoc> claim(
            YearMonth billingMonth,
            int limit,
            Duration leaseDuration,
            String workerId,
            Instant now,
            int partitionIndex,
            int partitionCount
    ) {
        if (limit <= 0) return List.of();

        LocalDate billingMonthDay = billingMonth.atDay(1);
        Instant leaseUntil = now.plus(leaseDuration);

        String sql = (partitionCount > 1)
                ? claimSqlWithPartition()
                : claimSqlNoPartition();

        Map<String, Object> params = Map.of(
                "billingMonthDay", Date.valueOf(billingMonthDay),
                "limit", limit,
                "workerId", workerId,
                "leaseUntil", Timestamp.from(leaseUntil),
                "now", Timestamp.from(now),
                "partitionIndex", partitionIndex,
                "partitionCount", partitionCount
        );

        List<ClaimedWorkDoc> claimed = jdbc.query(sql, params, (rs, rowNum) -> {
            LocalDate bmDay = rs.getDate("billing_month_day").toLocalDate();
            long userId = rs.getLong("user_id");
            int attemptCount = rs.getInt("attempt_count");
            Instant lease = rs.getTimestamp("lease_until").toInstant();
            String workId = bmDay + ":" + userId;
            return new ClaimedWorkDoc(workId, bmDay.toString(), userId, attemptCount, lease);
        });

        log.info("step3.claim billingMonth={} limit={} claimed={}", billingMonth, limit, claimed.size());
        return claimed;
    }

    private static String claimSqlNoPartition() {
        return """
            WITH candidates AS (
                SELECT billing_month_day, user_id
                FROM billing_work
                WHERE billing_month_day = :billingMonthDay
                  AND (
                        status = 'TARGET'
                        OR (status = 'PROCESSING' AND lease_until < :now)
                  )
                ORDER BY user_id
                LIMIT :limit
            )
            UPDATE billing_work bw
            SET status = 'PROCESSING',
                claimed_by = :workerId,
                lease_until = :leaseUntil,
                updated_at = :now,
                attempt_count = bw.attempt_count + 1
            FROM candidates c
            WHERE bw.billing_month_day = c.billing_month_day
              AND bw.user_id = c.user_id
            RETURNING bw.billing_month_day, bw.user_id, bw.attempt_count, bw.lease_until
            """;
    }

    private static String claimSqlWithPartition() {
        return """
            WITH candidates AS (
                SELECT billing_month_day, user_id
                FROM billing_work
                WHERE billing_month_day = :billingMonthDay
                  AND (
                        status = 'TARGET'
                        OR (status = 'PROCESSING' AND lease_until < :now)
                  )
                  AND (user_id % :partitionCount) = :partitionIndex
                ORDER BY user_id
                LIMIT :limit
            )
            UPDATE billing_work bw
            SET status = 'PROCESSING',
                claimed_by = :workerId,
                lease_until = :leaseUntil,
                updated_at = :now,
                attempt_count = bw.attempt_count + 1
            FROM candidates c
            WHERE bw.billing_month_day = c.billing_month_day
              AND bw.user_id = c.user_id
            RETURNING bw.billing_month_day, bw.user_id, bw.attempt_count, bw.lease_until
            """;
    }
}
