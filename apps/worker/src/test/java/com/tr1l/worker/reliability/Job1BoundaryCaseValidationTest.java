package com.tr1l.worker.reliability;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.tr1l.worker.reliability.dataset.BoundaryCaseResult;
import com.tr1l.worker.reliability.dataset.Job1DatasetDefinition;
import com.tr1l.worker.reliability.support.AllureEvidenceWriter;
import com.tr1l.worker.reliability.support.ReliabilityDataSourceFactory;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-reliability")
// 로컬 환경 전용 검증
// D2 를 적재하고 S-001 을 돌린 직후 경계값 경우별 결과를 대조
class Job1BoundaryCaseValidationTest {
    private static final String DATASET_ID = "D2-boundary";
    private static final String OVERAGE_LINE = "초과 데이터 사용량";

    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();

    @Test
    @DisplayName("D2 경계값마다 청구 대상, 기간 판정, 금액이 기대와 같은지 보기")
    void boundaryCasesShouldMatchExpectedOutcome() throws Exception {
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_RELIABILITY_ENABLED")) && DATASET_ID.equals(config.datasetId()),
                "Load D2-boundary and set JOB1_RELIABILITY_ENABLED=true to run boundary case validation"
        );

        Job1DatasetDefinition dataset = catalog.loadDataset(DATASET_ID);
        LocalDate billingMonthDay = LocalDate.parse(dataset.billingMonthDay());

        // 경우별 사용자 (main 디비 이름에 경우 코드가 들어 있다)
        NamedParameterJdbcTemplate main = new NamedParameterJdbcTemplate(dataSourceFactory.createMainDataSource(config));
        Map<String, List<Long>> usersByCase = new LinkedHashMap<>();
        main.query("""
                        SELECT user_id, regexp_replace(name, '^경계값(.*)-[0-9]+$', '\\1') AS code
                        FROM users
                        ORDER BY user_id
                        """,
                rs -> {
                    usersByCase.computeIfAbsent(rs.getString("code"), ignored -> new ArrayList<>()).add(rs.getLong("user_id"));
                });

        // Step1 결과
        NamedParameterJdbcTemplate target = new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
        Map<Long, Map<String, Long>> targets = new HashMap<>();
        target.query("""
                        SELECT user_id,
                               used_data_mb,
                               CASE WHEN has_contract THEN 1 ELSE 0 END AS has_contract,
                               CASE WHEN soldier_eligible THEN 1 ELSE 0 END AS soldier_eligible,
                               jsonb_array_length(coalesce(options_jsonb, '[]'::jsonb)) AS option_count
                        FROM billing_targets
                        WHERE billing_month = :billingMonthDay
                        """,
                new MapSqlParameterSource("billingMonthDay", billingMonthDay),
                rs -> {
                    Map<String, Long> row = new HashMap<>();
                    row.put("usedDataMb", rs.getLong("used_data_mb"));
                    row.put("hasContract", rs.getLong("has_contract"));
                    row.put("soldierEligible", rs.getLong("soldier_eligible"));
                    row.put("optionCount", rs.getLong("option_count"));
                    targets.put(rs.getLong("user_id"), row);
                });

        // snapshot 금액
        Map<Long, Map<String, Long>> amounts = new HashMap<>();
        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            Document filter = new Document("billingMonth", billingMonthDay.toString());
            for (Document snapshot : mongoClient.getDatabase(config.mongoDatabase()).getCollection(config.snapshotCollection()).find(filter)) {
                Document payload = snapshot.get("payload", Document.class);
                Map<String, Long> row = new HashMap<>();
                row.put("overageAmount", sumLines(payload.getList("chargeLines", Document.class, List.of()), "name", OVERAGE_LINE, "pricingSnapshot"));
                row.put("welfareDiscount", sumLines(payload.getList("discountLines", Document.class, List.of()), "discountType", "WELFARE", null));
                amounts.put(((Number) snapshot.get("userId")).longValue(), row);
            }
        }

        List<BoundaryCaseResult.Case> cases = new ArrayList<>();
        for (JsonNode expected : dataset.boundaryCases()) {
            String code = expected.get("code").asText();
            List<Long> users = usersByCase.getOrDefault(code, List.of());
            List<String> differences = new ArrayList<>();
            if (users.isEmpty()) {
                differences.add("no users for case");
            }

            for (Long userId : users) {
                boolean isTarget = targets.containsKey(userId);
                if (expected.get("target").asBoolean() != isTarget) {
                    differences.add("user " + userId + " target expected " + expected.get("target").asBoolean() + " actual " + isTarget);
                    continue;
                }
                if (!isTarget) {
                    continue;
                }
                compare(differences, userId, expected, "usedDataMb", targets.get(userId));
                compareFlag(differences, userId, expected, "hasContract", targets.get(userId));
                compareFlag(differences, userId, expected, "soldierEligible", targets.get(userId));
                compare(differences, userId, expected, "optionCount", targets.get(userId));
                compare(differences, userId, expected, "overageAmount", amounts.getOrDefault(userId, Map.of()));
                compare(differences, userId, expected, "welfareDiscount", amounts.getOrDefault(userId, Map.of()));
            }

            cases.add(new BoundaryCaseResult.Case(
                    code,
                    expected.path("decision").asText("current"),
                    expected.path("note").asText(null),
                    users.size(),
                    differences.isEmpty(),
                    differences.size() > 20 ? differences.subList(0, 20) : differences
            ));
        }

        BoundaryCaseResult result = new BoundaryCaseResult(DATASET_ID, Instant.now(), cases);

        // 결과 파일 저장
        Path output = Paths.get("build", "job1-reliability-results", "dataset", DATASET_ID + "-boundary-cases.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, ReliabilityObjectMappers.json().writeValueAsString(result), StandardCharsets.UTF_8);
        evidenceWriter.attachJson("boundary-cases.json", result);

        assertThat(result.failedCases())
                .as("기대와 다른 경계값 경우")
                .isEmpty();
    }

    // 숫자 항목 비교. 기대값이 없는 항목은 보지 않는다
    private void compare(List<String> differences, long userId, JsonNode expected, String field, Map<String, Long> actual) {
        if (!expected.has(field)) {
            return;
        }
        long actualValue = actual.getOrDefault(field, 0L);
        if (expected.get(field).asLong() != actualValue) {
            differences.add("user " + userId + " " + field + " expected " + expected.get(field).asLong() + " actual " + actualValue);
        }
    }

    // 참거짓 항목 비교
    private void compareFlag(List<String> differences, long userId, JsonNode expected, String field, Map<String, Long> actual) {
        if (!expected.has(field)) {
            return;
        }
        boolean actualValue = actual.getOrDefault(field, 0L) == 1L;
        if (expected.get(field).asBoolean() != actualValue) {
            differences.add("user " + userId + " " + field + " expected " + expected.get(field).asBoolean() + " actual " + actualValue);
        }
    }

    // 조건에 맞는 청구·할인 항목 금액 합
    private long sumLines(List<Document> lines, String key, String value, String nested) {
        return lines.stream()
                .filter(line -> value.equals(line.getString(key)))
                .mapToLong(line -> {
                    Document money = nested == null
                            ? line.get("discountAmount", Document.class)
                            : line.get(nested, Document.class).get("amount", Document.class);
                    return ((Number) money.get("value")).longValue();
                })
                .sum();
    }
}
