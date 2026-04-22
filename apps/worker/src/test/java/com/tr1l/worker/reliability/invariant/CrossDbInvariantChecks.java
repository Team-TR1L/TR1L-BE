package com.tr1l.worker.reliability.invariant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
import org.bson.Document;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// postgres 몽고 최종 상태 대조
// 부분 성공 경계 우선 검증
public final class CrossDbInvariantChecks {
    private static final int MAX_SAMPLES = 10;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MongoClient mongoClient;
    private final ReliabilityRuntimeConfig runtimeConfig;
    private final ReliabilityResourceCatalog catalog;

    public CrossDbInvariantChecks(
            NamedParameterJdbcTemplate jdbcTemplate,
            MongoClient mongoClient,
            ReliabilityRuntimeConfig runtimeConfig,
            ReliabilityResourceCatalog catalog
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoClient = mongoClient;
        this.runtimeConfig = runtimeConfig;
        this.catalog = catalog;
    }

    public InvariantResult execute(InvariantDefinition definition, LocalDate billingMonthDay) {
        // yaml 명세 우선 적용
        CrossDbCheckSpec spec = catalog.loadCheckText(definition.checkRef()).isBlank()
                ? null
                : parseSpec(definition.checkRef());

        // 기본 calculated workId 조회 쿼리
        String postgresQuery = spec == null ? """
                SELECT to_char(billing_month_day, 'YYYY-MM-DD') || ':' || user_id AS work_id
                FROM billing_work
                WHERE billing_month_day = :billingMonthDay
                  AND status = 'CALCULATED'
                """ : spec.postgresQuery();

        Set<String> calculatedWorkIds = jdbcTemplate.queryForList(
                        postgresQuery,
                        new MapSqlParameterSource("billingMonthDay", billingMonthDay),
                        String.class
                )
                .stream()
                .collect(Collectors.toSet());

        String collectionName = spec == null ? runtimeConfig.snapshotCollection() : spec.mongo().collection();
        String workIdField = spec == null ? "workId" : spec.mongo().workIdField();
        String billingMonthField = spec == null ? "billingMonth" : spec.mongo().billingMonthField();

        // 몽고 동일 billingMonth 범위 조회
        MongoCollection<Document> collection = mongoClient
                .getDatabase(runtimeConfig.mongoDatabase())
                .getCollection(collectionName);

        Set<String> mongoWorkIds = collection.find(Filters.eq(billingMonthField, billingMonthDay.toString()))
                .projection(new Document(workIdField, 1).append("_id", 0))
                .into(new java.util.ArrayList<>())
                .stream()
                .map(document -> document.getString(workIdField))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // 전체 누락 건수 집계
        // 샘플 행 제한 첨부
        long missingCount = calculatedWorkIds.stream()
                .filter(workId -> !mongoWorkIds.contains(workId))
                .count();

        List<Map<String, Object>> missing = calculatedWorkIds.stream()
                .filter(workId -> !mongoWorkIds.contains(workId))
                .sorted()
                .limit(MAX_SAMPLES)
                .map(workId -> Map.<String, Object>of("workId", workId))
                .toList();

        return new InvariantResult(
                definition.id(),
                definition.title(),
                definition.scope(),
                definition.checkType(),
                missingCount == definition.expectedViolationCount(),
                Math.toIntExact(missingCount),
                missing
        );
    }

    private CrossDbCheckSpec parseSpec(String classpathLocation) {
        try {
            // 명세 파싱 실패 시 예외 전파
            return ReliabilityObjectMappers.yaml()
                    .readValue(catalog.loadCheckText(classpathLocation), CrossDbCheckSpec.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse cross-db check spec: " + classpathLocation, e);
        }
    }

    private record CrossDbCheckSpec(
            String id,
            String postgresQuery,
            Mongo mongo
    ) {
    }

    private record Mongo(
            String collection,
            String workIdField,
            String billingMonthField
    ) {
    }
}
