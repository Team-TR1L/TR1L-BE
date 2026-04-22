package com.tr1l.worker.reliability.invariant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 상태 카운트 수집기
// 시나리오 공통 수치 집약
public final class Job1StateCollector {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MongoClient mongoClient;
    private final String mongoDatabase;
    private final String snapshotCollection;

    public Job1StateCollector(
            NamedParameterJdbcTemplate jdbcTemplate,
            MongoClient mongoClient,
            String mongoDatabase,
            String snapshotCollection
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoClient = mongoClient;
        this.mongoDatabase = mongoDatabase;
        this.snapshotCollection = snapshotCollection;
    }

    public Job1StateSnapshot collect(LocalDate billingMonthDay) {
        MapSqlParameterSource params = new MapSqlParameterSource("billingMonthDay", billingMonthDay);

        // 명시적 카운트 쿼리 유지
        // 보고서 대조 기준
        long billingTargetsCount = queryForLong("""
                SELECT COUNT(*)
                FROM billing_targets
                WHERE billing_month = :billingMonthDay
                """, params);

        long billingWorkCount = queryForLong("""
                SELECT COUNT(*)
                FROM billing_work
                WHERE billing_month_day = :billingMonthDay
                """, params);

        Map<String, Long> statusCounts = jdbcTemplate.query("""
                        SELECT status, COUNT(*) AS count
                        FROM billing_work
                        WHERE billing_month_day = :billingMonthDay
                        GROUP BY status
                        """,
                params,
                rs -> {
                    java.util.Map<String, Long> counts = new java.util.HashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getString("status"), rs.getLong("count"));
                    }
                    return counts;
                });

        // stale PROCESSING 집계
        // lease 만료 상태 기준
        long staleProcessingCount = queryForLong("""
                SELECT COUNT(*)
                FROM billing_work
                WHERE billing_month_day = :billingMonthDay
                  AND status = 'PROCESSING'
                  AND lease_until < now()
                """, params);

        long duplicateBillingWorkCount = queryForLong("""
                SELECT COUNT(*)
                FROM (
                    SELECT user_id
                    FROM billing_work
                    WHERE billing_month_day = :billingMonthDay
                    GROUP BY user_id
                    HAVING COUNT(*) > 1
                ) duplicates
                """, params);

        // 스냅샷 동일 월 범위
        MongoCollection<Document> collection = mongoClient
                .getDatabase(mongoDatabase)
                .getCollection(snapshotCollection);

        // 몽고 중복 workId 집계
        long mongoSnapshotCount = collection.countDocuments(Filters.eq("billingMonth", billingMonthDay.toString()));

        List<Document> duplicateDocs = collection.aggregate(List.of(
                Aggregates.match(Filters.eq("billingMonth", billingMonthDay.toString())),
                Aggregates.group("$workId", Accumulators.sum("count", 1)),
                Aggregates.match(Filters.gt("count", 1)),
                Aggregates.limit(10)
        )).into(new java.util.ArrayList<>());

        // 스냅샷 선반영 workId 수집
        Set<String> snapshotWorkIds = new HashSet<>(collection.find(Filters.eq("billingMonth", billingMonthDay.toString()))
                .projection(new Document("workId", 1).append("_id", 0))
                .into(new java.util.ArrayList<>())
                .stream()
                .map(doc -> doc.getString("workId"))
                .filter(java.util.Objects::nonNull)
                .toList());

        List<String> processingWorkIds = jdbcTemplate.query("""
                        SELECT billing_month_day, user_id
                        FROM billing_work
                        WHERE billing_month_day = :billingMonthDay
                          AND status = 'PROCESSING'
                        ORDER BY user_id
                        """,
                params,
                (rs, rowNum) -> rs.getDate("billing_month_day").toLocalDate() + ":" + rs.getLong("user_id"));

        List<String> targetWorkIds = jdbcTemplate.query("""
                        SELECT billing_month_day, user_id
                        FROM billing_work
                        WHERE billing_month_day = :billingMonthDay
                          AND status = 'TARGET'
                        ORDER BY user_id
                        """,
                params,
                (rs, rowNum) -> rs.getDate("billing_month_day").toLocalDate() + ":" + rs.getLong("user_id"));

        List<String> targetWithSnapshotWorkIds = targetWorkIds.stream()
                .filter(snapshotWorkIds::contains)
                .toList();

        List<String> processingWithSnapshotWorkIds = processingWorkIds.stream()
                .filter(snapshotWorkIds::contains)
                .toList();

        // 샘플 행 제한 수집
        // 발표 디버깅 공용 근거
        return new Job1StateSnapshot(
                billingMonthDay.toString(),
                Instant.now(),
                billingTargetsCount,
                billingWorkCount,
                statusCounts.getOrDefault("TARGET", 0L),
                targetWithSnapshotWorkIds.size(),
                targetWorkIds.size() - targetWithSnapshotWorkIds.size(),
                statusCounts.getOrDefault("PROCESSING", 0L),
                statusCounts.getOrDefault("CALCULATED", 0L),
                statusCounts.getOrDefault("FAILED", 0L),
                staleProcessingCount,
                duplicateBillingWorkCount,
                mongoSnapshotCount,
                duplicateDocs.size(),
                processingWithSnapshotWorkIds.size(),
                processingWorkIds.size() - processingWithSnapshotWorkIds.size(),
                targetWithSnapshotWorkIds.stream().limit(10).toList(),
                processingWithSnapshotWorkIds.stream().limit(10).toList(),
                duplicateDocs.stream().map(doc -> doc.getString("_id")).toList()
        );
    }

    private long queryForLong(String sql, MapSqlParameterSource params) {
        // 공통 count 조회
        Long result = jdbcTemplate.queryForObject(sql, params, Long.class);
        return result == null ? 0 : result;
    }
}
