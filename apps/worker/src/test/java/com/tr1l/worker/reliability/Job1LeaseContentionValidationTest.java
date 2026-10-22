package com.tr1l.worker.reliability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.tr1l.worker.reliability.fault.FaultPointExpectation;
import com.tr1l.worker.reliability.fault.FaultPoints;
import com.tr1l.worker.reliability.invariant.CrossDbInvariantChecks;
import com.tr1l.worker.reliability.invariant.InvariantValidationResult;
import com.tr1l.worker.reliability.invariant.InvariantValidator;
import com.tr1l.worker.reliability.invariant.Job1StateCollector;
import com.tr1l.worker.reliability.invariant.Job1StateSnapshot;
import com.tr1l.worker.reliability.invariant.PostgresInvariantChecks;
import com.tr1l.worker.reliability.scenario.Job1ScenarioDefinition;
import com.tr1l.worker.reliability.support.AllureEvidenceWriter;
import com.tr1l.worker.reliability.support.JsonResultWriter;
import com.tr1l.worker.reliability.support.ReliabilityDataSourceFactory;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-reliability")
// 로컬 환경 전용 검증
// lease 와 worker 경합
//   S-002  Step3 claim 직후 프로세스가 죽어 PROCESSING 이 lease 안쪽으로 남는다
//   S-002E lease 가 끝나기 전에 다시 돌리면 남은 PROCESSING 을 가져가지 않고 billing_cycle 도 마감하지 않는다
//   S-002R lease 가 끝난 뒤 다시 돌리면 남은 PROCESSING 을 한 번만 다시 가져가 S-001 과 같아진다
//   S-011  같은 cutoff 로 worker 두 개가 Step3 를 같이 돌아도 work 마다 한 번만 계산한다
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Job1LeaseContentionValidationTest {
    static final String PROCESSING_LEFT = "processing-left.json";
    static final String LEASE_CHECK = "lease-check.json";

    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final JsonResultWriter resultWriter = new JsonResultWriter();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();
    private final Job1RerunConvergence convergence = new Job1RerunConvergence(catalog, resultWriter, evidenceWriter);
    private final FaultPointExpectation expectation = new FaultPointExpectation();

    @Test
    @Order(1)
    @DisplayName("S-002 claim 직후 프로세스가 죽으면 PROCESSING 이 lease 안쪽으로 남는지 보기")
    void claimHaltShouldLeaveProcessingWithinLease() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-002");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story());

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());

            Job1StateSnapshot snapshot =
                    new Job1StateCollector(jdbcTemplate, mongoClient, config.mongoDatabase(), config.snapshotCollection())
                            .collect(billingMonthDay);
            InvariantValidationResult validationResult = validator(jdbcTemplate, mongoClient, config)
                    .validate(scenario, catalog.loadActiveInvariants());
            FaultPoints.Point p3a = catalog.loadFaultPoints().points().get("P3a");
            FaultPointExpectation.Result expected =
                    expectation.evaluate("P3a K2", p3a.expectFor("K2"), snapshot, targetUsers(config));

            Instant now = Instant.now();
            List<WorkClaim> left = works(jdbcTemplate, billingMonthDay).stream()
                    .filter(work -> "PROCESSING".equals(work.status()))
                    .toList();
            long expired = left.stream().filter(work -> !work.leaseUntil().isAfter(now)).count();
            Instant earliestLease = left.stream().map(WorkClaim::leaseUntil).min(Instant::compareTo).orElse(null);

            LeaseCheck check = new LeaseCheck("PROCESSING_LEFT", !left.isEmpty() && expired == 0, expired,
                    "lease 안쪽 PROCESSING " + left.size() + "건, 가장 이른 만료 " + earliestLease,
                    metrics("processingLeft", left.size(), "expired", expired,
                            "calculated", snapshot.calculatedCount(), "target", snapshot.targetCount()));

            resultWriter.write(scenario, "fault-state-snapshot.json", snapshot);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);
            resultWriter.write(scenario, "fault-expectation.json", expected);
            resultWriter.write(scenario, PROCESSING_LEFT, left);
            resultWriter.write(scenario, LEASE_CHECK, check);
            evidenceWriter.attachJson(LEASE_CHECK, check);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(expected.matched()).as("fault expectation %s", expected).isTrue();
            assertThat(left).as("claim 직후 장애 뒤 남은 PROCESSING").isNotEmpty();
            assertThat(expired).as("검증 시점에 이미 lease 가 끝난 PROCESSING").isZero();
        }
    }

    @Test
    @Order(2)
    @DisplayName("S-002E lease 가 끝나기 전 재실행은 남은 PROCESSING 을 가져가지 않고 billing_cycle 을 마감하지 않는지 보기")
    void rerunBeforeLeaseExpiryShouldNotTakeLeasedWork() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-002E");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story());
        Map<Long, WorkClaim> left = processingLeft();

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());

            InvariantValidationResult validationResult = validator(jdbcTemplate, mongoClient, config)
                    .validate(scenario, catalog.loadActiveInvariants());
            List<WorkClaim> current = works(jdbcTemplate, billingMonthDay);

            // 남은 PROCESSING 은 상태, 선점한 worker, 시도 횟수, lease 가 모두 그대로여야 한다
            long untouched = current.stream()
                    .filter(work -> left.containsKey(work.userId()))
                    .filter(work -> work.sameClaimAs(left.get(work.userId())))
                    .count();
            // 나머지는 이번 재실행이 한 번에 끝내야 한다
            List<WorkClaim> others = current.stream().filter(work -> !left.containsKey(work.userId())).toList();
            long othersNotCalculated = others.stream().filter(work -> !"CALCULATED".equals(work.status())).count();
            long othersRetried = others.stream().filter(work -> work.attemptCount() != 1).count();
            String cycleStatus = cycleStatus(jdbcTemplate, billingMonthDay);

            long violations = (left.size() - untouched) + othersNotCalculated + othersRetried
                    + ("RUNNING".equals(cycleStatus) ? 0 : 1);
            LeaseCheck check = new LeaseCheck("LEASE_HOLD", violations == 0 && validationResult.allPassed(), violations,
                    "lease 안쪽 PROCESSING " + untouched + " / " + left.size() + " 그대로, 나머지 미완료 "
                            + othersNotCalculated + ", billing_cycle " + cycleStatus,
                    metrics("processingLeft", left.size(), "untouched", untouched,
                            "othersNotCalculated", othersNotCalculated, "othersRetried", othersRetried,
                            "cycleStatus", cycleStatus));

            resultWriter.write(scenario, "invariant-validation.json", validationResult);
            resultWriter.write(scenario, LEASE_CHECK, check);
            evidenceWriter.attachJson(LEASE_CHECK, check);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(untouched).as("lease 안쪽 PROCESSING 을 가져가지 않음").isEqualTo(left.size());
            assertThat(othersNotCalculated).as("남은 PROCESSING 말고는 모두 CALCULATED").isZero();
            assertThat(othersRetried).as("나머지 work 는 한 번만 claim").isZero();
            assertThat(cycleStatus).as("끝나지 않은 work 가 있으면 마감하지 않음").isEqualTo("RUNNING");
        }
    }

    @Test
    @Order(3)
    @DisplayName("S-002R lease 가 끝난 뒤 재실행은 남은 PROCESSING 을 한 번만 다시 가져가 S-001 과 같아지는지 보기")
    void rerunAfterLeaseExpiryShouldReclaimOnceAndConverge() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-002R");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story());
        Map<Long, WorkClaim> left = processingLeft();

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());

            List<WorkClaim> current = works(jdbcTemplate, billingMonthDay);
            // 남은 PROCESSING 은 다른 worker 가 한 번 더 claim 해서 끝냈어야 한다
            long reclaimed = current.stream()
                    .filter(work -> left.containsKey(work.userId()))
                    .filter(work -> work.reclaimedOnceFrom(left.get(work.userId())))
                    .count();
            long othersRetried = current.stream()
                    .filter(work -> !left.containsKey(work.userId()))
                    .filter(work -> work.attemptCount() != 1)
                    .count();
            String cycleStatus = cycleStatus(jdbcTemplate, billingMonthDay);

            long violations = (left.size() - reclaimed) + othersRetried + ("FINISHED".equals(cycleStatus) ? 0 : 1);
            LeaseCheck check = new LeaseCheck("LEASE_RECLAIM", violations == 0, violations,
                    "lease 끝난 PROCESSING " + reclaimed + " / " + left.size() + " 한 번만 재선점, billing_cycle " + cycleStatus,
                    metrics("processingLeft", left.size(), "reclaimedOnce", reclaimed,
                            "othersRetried", othersRetried, "cycleStatus", cycleStatus));
            resultWriter.write(scenario, LEASE_CHECK, check);
            evidenceWriter.attachJson(LEASE_CHECK, check);

            convergence.verify(scenario, jdbcTemplate, mongoClient, config);
            assertThat(reclaimed).as("lease 끝난 PROCESSING 을 한 번만 다시 가져감").isEqualTo(left.size());
            assertThat(othersRetried).as("나머지 work 는 한 번만 claim").isZero();
            assertThat(cycleStatus).isEqualTo("FINISHED");
        }
    }

    @Test
    @Order(4)
    @DisplayName("S-011 같은 cutoff 로 worker 두 개가 Step3 를 같이 돌아도 work 마다 한 번만 계산하는지 보기")
    void concurrentWorkersShouldCalculateEachWorkOnce() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-011");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story() + " " + System.getenv("JOB1_FAULT_TIMING"));

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());

            List<WorkClaim> current = works(jdbcTemplate, billingMonthDay);
            long retried = current.stream().filter(work -> work.attemptCount() != 1).count();
            // claimed_by 는 "<pid>@<host>-<partition>" 이다. 프로세스별로 몇 건을 계산했는지 센다
            Map<String, Long> byWorker = current.stream()
                    .filter(work -> work.claimedBy() != null)
                    .collect(Collectors.groupingBy(work -> work.claimedBy().substring(0, work.claimedBy().lastIndexOf('-')),
                            TreeMap::new, Collectors.counting()));
            String cycleStatus = cycleStatus(jdbcTemplate, billingMonthDay);

            long violations = retried + ("FINISHED".equals(cycleStatus) ? 0 : 1);
            LeaseCheck check = new LeaseCheck("CLAIM_ONCE", violations == 0, violations,
                    "두 번 이상 claim 된 work " + retried + ", worker 별 계산 " + byWorker.values()
                            + ", billing_cycle " + cycleStatus,
                    metrics("retried", retried, "workers", byWorker, "cycleStatus", cycleStatus));
            resultWriter.write(scenario, LEASE_CHECK, check);
            evidenceWriter.attachJson(LEASE_CHECK, check);

            convergence.verify(scenario, jdbcTemplate, mongoClient, config);
            assertThat(retried).as("두 worker 가 같은 work 를 claim 하지 않음").isZero();
            assertThat(cycleStatus).isEqualTo("FINISHED");
        }
    }

    // S-002 가 남긴 PROCESSING 목록
    private Map<Long, WorkClaim> processingLeft() throws Exception {
        Path path = resultWriter.scenarioDirectory(catalog.loadScenario("S-002")).resolve(PROCESSING_LEFT);
        assertThat(Files.exists(path)).as("Run S-002 first to produce %s", path).isTrue();
        List<WorkClaim> left = ReliabilityObjectMappers.json()
                .readValue(Files.readString(path), new TypeReference<List<WorkClaim>>() {
                });
        assertThat(left).as("S-002 가 남긴 PROCESSING").isNotEmpty();
        return left.stream().collect(Collectors.toMap(WorkClaim::userId, Function.identity()));
    }

    private List<WorkClaim> works(NamedParameterJdbcTemplate jdbcTemplate, LocalDate billingMonthDay) {
        return jdbcTemplate.query("""
                        SELECT user_id, status, claimed_by, attempt_count, lease_until
                        FROM billing_work
                        WHERE billing_month_day = :billingMonthDay
                        ORDER BY user_id
                        """,
                new MapSqlParameterSource("billingMonthDay", Date.valueOf(billingMonthDay)),
                (rs, rowNum) -> new WorkClaim(
                        rs.getLong("user_id"),
                        rs.getString("status"),
                        rs.getString("claimed_by"),
                        rs.getInt("attempt_count"),
                        rs.getTimestamp("lease_until") == null ? null : rs.getTimestamp("lease_until").toInstant()
                ));
    }

    private String cycleStatus(NamedParameterJdbcTemplate jdbcTemplate, LocalDate billingMonthDay) {
        List<String> status = jdbcTemplate.queryForList(
                "SELECT status FROM billing_cycle WHERE billing_month = :billingMonth",
                new MapSqlParameterSource("billingMonth", Date.valueOf(billingMonthDay)),
                String.class);
        return status.isEmpty() ? "NONE" : status.get(0);
    }

    // 청구 대상 사용자 수 (main 디비 기준)
    private long targetUsers(ReliabilityRuntimeConfig config) {
        Long count = new NamedParameterJdbcTemplate(dataSourceFactory.createMainDataSource(config)).getJdbcTemplate()
                .queryForObject("""
                        SELECT count(*)
                        FROM users
                        WHERE user_role = 'USER' AND user_status = 'ACTIVE' AND plan_code IS NOT NULL
                        """, Long.class);
        return count == null ? 0 : count;
    }

    private InvariantValidator validator(NamedParameterJdbcTemplate jdbcTemplate, MongoClient mongoClient,
                                         ReliabilityRuntimeConfig config) {
        return new InvariantValidator(
                new PostgresInvariantChecks(jdbcTemplate, catalog),
                new CrossDbInvariantChecks(jdbcTemplate, mongoClient, config, catalog)
        );
    }

    private static Map<String, Object> metrics(Object... keyValues) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            metrics.put((String) keyValues[i], keyValues[i + 1]);
        }
        return metrics;
    }

    private void assumeLocalReliabilityEnabled() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_RELIABILITY_ENABLED")),
                "Set JOB1_RELIABILITY_ENABLED=true to run local Job1 lease validation"
        );
    }

    // billing_work 한 행의 claim 정보
    record WorkClaim(long userId, String status, String claimedBy, int attemptCount, Instant leaseUntil) {
        boolean sameClaimAs(WorkClaim before) {
            return "PROCESSING".equals(status)
                    && Objects.equals(claimedBy, before.claimedBy())
                    && attemptCount == before.attemptCount()
                    && Objects.equals(leaseUntil, before.leaseUntil());
        }

        boolean reclaimedOnceFrom(WorkClaim before) {
            return "CALCULATED".equals(status)
                    && attemptCount == before.attemptCount() + 1
                    && !Objects.equals(claimedBy, before.claimedBy());
        }
    }

    // 하네스가 check_result 에 옮겨 적는 결과
    record LeaseCheck(String checkId, boolean passed, long violationCount, String detail, Map<String, Object> metrics) {
    }
}
