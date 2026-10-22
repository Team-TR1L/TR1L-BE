package com.tr1l.worker.reliability;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.tr1l.worker.reliability.content.ContentValidation;
import com.tr1l.worker.reliability.content.SnapshotHashCollector;
import com.tr1l.worker.reliability.content.SnapshotHashComparator;
import com.tr1l.worker.reliability.content.SnapshotHashComparison;
import com.tr1l.worker.reliability.content.SnapshotHashes;
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
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-reliability")
// 로컬 환경 전용 검증
// 반복 실패와 완료 월 재실행
//   S-012  재실행 도중 다시 장애. 앞 실행이 끝낸 일을 잃거나 망가뜨리지 않았는지 본다
//   S-012R 장애를 몇 번 겪은 뒤 마지막 재실행이 S-001 과 같아지는지 본다
//   S-013  마감된 월을 다시 돌리면 Step0 에서 NOOP 으로 끝나고 아무것도 바뀌지 않는지 본다
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Job1RepeatedFaultValidationTest {
    static final String REPEAT_CHECK = "repeat-check.json";
    static final String MONTH_BEFORE = "finished-month-before.json";

    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final JsonResultWriter resultWriter = new JsonResultWriter();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();
    private final Job1RerunConvergence convergence = new Job1RerunConvergence(catalog, resultWriter, evidenceWriter);

    @Test
    @Order(1)
    @DisplayName("S-012 재실행 도중 다시 장애가 나도 앞에서 끝낸 일을 잃거나 바꾸지 않는지 보기")
    void faultDuringRerunShouldKeepProgress() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-012");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story() + " " + System.getenv("JOB1_FAULT_POINT"));
        long calculatedBefore = Long.parseLong(System.getenv().getOrDefault("JOB1_CALCULATED_BEFORE", "0"));

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());

            Job1StateSnapshot snapshot =
                    new Job1StateCollector(jdbcTemplate, mongoClient, config.mongoDatabase(), config.snapshotCollection())
                            .collect(billingMonthDay);
            InvariantValidationResult validationResult = validator(jdbcTemplate, mongoClient, config)
                    .validate(scenario, catalog.loadActiveInvariants());
            // 지금 있는 snapshot 은 모두 S-001 과 같은 내용이어야 한다. 아직 없는 사용자는 세지 않는다
            SnapshotHashComparison hashes = partialHashComparison(scenario, mongoClient, config, billingMonthDay);
            String cycleStatus = cycleStatus(jdbcTemplate, billingMonthDay);
            boolean prematureFinish = "FINISHED".equals(cycleStatus)
                    && snapshot.calculatedCount() < snapshot.billingWorkCount();

            long lost = Math.max(calculatedBefore - snapshot.calculatedCount(), 0);
            long violations = lost + hashes.hashMismatch() + hashes.extraInCurrent() + hashes.duplicateUsers()
                    + snapshot.duplicateMongoSnapshotCount() + (prematureFinish ? 1 : 0);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("calculatedBefore", calculatedBefore);
            metrics.put("calculatedAfter", snapshot.calculatedCount());
            metrics.put("snapshotHashMatch", hashes.hashMatch());
            metrics.put("snapshotHashMismatch", hashes.hashMismatch());
            metrics.put("cycleStatus", cycleStatus);
            RepeatCheck check = new RepeatCheck("PROGRESS_KEPT", violations == 0, violations,
                    "CALCULATED " + calculatedBefore + " → " + snapshot.calculatedCount()
                            + ", 있는 snapshot " + hashes.hashMatch() + "건 S-001 과 같음, 다름 " + hashes.hashMismatch()
                            + ", billing_cycle " + cycleStatus,
                    metrics);

            resultWriter.write(scenario, "fault-state-snapshot.json", snapshot);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);
            resultWriter.write(scenario, REPEAT_CHECK, check);
            evidenceWriter.attachJson(REPEAT_CHECK, check);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(lost).as("앞 실행이 끝낸 CALCULATED 를 잃음").isZero();
            assertThat(hashes.hashMismatch()).as("이미 있는 snapshot 내용이 S-001 과 다름").isZero();
            assertThat(hashes.extraInCurrent() + hashes.duplicateUsers()).isZero();
            assertThat(snapshot.duplicateMongoSnapshotCount()).isZero();
            assertThat(prematureFinish).as("끝나지 않은 work 가 있는데 마감함").isFalse();
        }
    }

    @Test
    @Order(2)
    @DisplayName("S-012R 장애를 여러 번 겪은 뒤 마지막 재실행이 S-001 run 직후 상태와 내용으로 돌아오는지 보기")
    void rerunAfterRepeatedFaultsShouldConverge() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-012R");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story());

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            convergence.verify(scenario, jdbcTemplate, mongoClient, config);
            assertThat(cycleStatus(jdbcTemplate, LocalDate.parse(scenario.job().billingMonthDay()))).isEqualTo("FINISHED");
        }
    }

    @Test
    @Order(3)
    @DisplayName("S-013 다시 돌리기 전 마감된 월의 상태를 남기기")
    void captureFinishedMonth() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-013");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());
            assertThat(cycleStatus(jdbcTemplate, billingMonthDay)).as("마감된 월에서만 돈다").isEqualTo("FINISHED");

            resultWriter.write(scenario, MONTH_BEFORE, monthDigest(jdbcTemplate, mongoClient, config, billingMonthDay));
        }
    }

    @Test
    @Order(4)
    @DisplayName("S-013 마감된 월을 다시 돌리면 Step0 NOOP 으로 끝나고 아무것도 바뀌지 않는지 보기")
    void finishedMonthRerunShouldChangeNothing() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-013");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story() + " cutoff=" + System.getenv("JOB1_CUTOFF"));
        Path beforePath = resultWriter.scenarioDirectory(scenario).resolve(MONTH_BEFORE);
        assertThat(Files.exists(beforePath)).as("captureFinishedMonth 를 먼저 돌린다").isTrue();
        MonthDigest before = ReliabilityObjectMappers.json().readValue(Files.readString(beforePath), MonthDigest.class);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());
            MonthDigest after = monthDigest(jdbcTemplate, mongoClient, config, billingMonthDay);
            List<StepRun> steps = lastCalculateJobSteps(config);

            // 마감된 월이면 Step0 만 돌고 NOOP 으로 끝나야 한다
            boolean onlyGateNoop = steps.size() == 1
                    && "billingGateStep".equals(steps.get(0).stepName())
                    && "NOOP".equals(steps.get(0).exitCode());
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("before", before);
            metrics.put("after", after);
            metrics.put("steps", steps);
            long violations = before.differences(after) + (onlyGateNoop ? 0 : 1);
            RepeatCheck check = new RepeatCheck("MONTH_UNCHANGED", violations == 0, violations,
                    "billing_work, billing_targets, billing_cycle, snapshot 다른 표 " + before.differences(after)
                            + ", 실행한 step " + steps.stream().map(s -> s.stepName() + "=" + s.exitCode()).toList(),
                    metrics);
            resultWriter.write(scenario, REPEAT_CHECK, check);
            evidenceWriter.attachJson(REPEAT_CHECK, check);

            assertThat(after).as("마감된 월의 상태").isEqualTo(before);
            assertThat(onlyGateNoop).as("Step0 NOOP 으로만 끝남 %s", steps).isTrue();
        }
    }

    // billing_work, billing_targets, billing_cycle 의 행 전체와 snapshot 문서 전체(수정 시각 포함)를 해시한다
    private MonthDigest monthDigest(NamedParameterJdbcTemplate jdbcTemplate, MongoClient mongoClient,
                                    ReliabilityRuntimeConfig config, LocalDate billingMonthDay) throws Exception {
        MapSqlParameterSource params = new MapSqlParameterSource("month", Date.valueOf(billingMonthDay));
        String work = jdbcTemplate.queryForObject("""
                SELECT count(*) || ':' || coalesce(md5(string_agg(t::text, '|' ORDER BY user_id)), '')
                FROM billing_work t WHERE billing_month_day = :month
                """, params, String.class);
        String targets = jdbcTemplate.queryForObject("""
                SELECT count(*) || ':' || coalesce(md5(string_agg(t::text, '|' ORDER BY user_id)), '')
                FROM billing_targets t WHERE billing_month = :month
                """, params, String.class);
        String cycle = jdbcTemplate.queryForObject("""
                SELECT count(*) || ':' || coalesce(md5(string_agg(t::text, '|')), '')
                FROM billing_cycle t WHERE billing_month = :month
                """, params, String.class);

        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        long documents = 0;
        for (Document document : mongoClient.getDatabase(config.mongoDatabase())
                .getCollection(config.snapshotCollection())
                .find(Filters.eq("billingMonth", billingMonthDay.toString()))
                .sort(Sorts.ascending("_id"))) {
            sha.update(document.toJson().getBytes(StandardCharsets.UTF_8));
            documents++;
        }
        return new MonthDigest(work, targets, cycle, documents + ":" + HexFormat.of().formatHex(sha.digest()));
    }

    // 가장 최근 calculateJob 실행의 step 과 종료 상태 (batch 메타 테이블은 main DB 에 있다)
    private List<StepRun> lastCalculateJobSteps(ReliabilityRuntimeConfig config) {
        return new NamedParameterJdbcTemplate(dataSourceFactory.createMainDataSource(config)).query("""
                SELECT s.step_name, s.exit_code
                FROM batch_step_execution s
                WHERE s.job_execution_id = (
                    SELECT max(e.job_execution_id)
                    FROM batch_job_execution e JOIN batch_job_instance i USING (job_instance_id)
                    WHERE i.job_name = 'calculateJob')
                ORDER BY s.step_execution_id
                """, new MapSqlParameterSource(), (rs, n) -> new StepRun(rs.getString(1), rs.getString(2)));
    }

    private SnapshotHashComparison partialHashComparison(Job1ScenarioDefinition scenario, MongoClient mongoClient,
                                                         ReliabilityRuntimeConfig config, LocalDate billingMonthDay)
            throws Exception {
        Path baselinePath = resultWriter.scenarioDirectory(catalog.loadScenario("S-001"))
                .resolve(ContentValidation.BASELINE_HASHES);
        assertThat(Files.exists(baselinePath)).as("Run S-001 first to produce %s", baselinePath).isTrue();
        SnapshotHashes baseline =
                ReliabilityObjectMappers.json().readValue(Files.readString(baselinePath), SnapshotHashes.class);
        SnapshotHashes current = new SnapshotHashCollector(mongoClient, config.mongoDatabase(), config.snapshotCollection())
                .collect(billingMonthDay);
        SnapshotHashComparison comparison = new SnapshotHashComparator().compare(scenario.id(), "S-001", baseline, current);
        // C5 결과 파일과 이름을 다르게 둔다. 중간 상태라 아직 없는 사용자가 있는 것이 정상이다
        resultWriter.write(scenario, "partial-snapshot-hash-comparison.json", comparison);
        return comparison;
    }

    private String cycleStatus(NamedParameterJdbcTemplate jdbcTemplate, LocalDate billingMonthDay) {
        List<String> status = jdbcTemplate.queryForList(
                "SELECT status FROM billing_cycle WHERE billing_month = :billingMonth",
                new MapSqlParameterSource("billingMonth", Date.valueOf(billingMonthDay)),
                String.class);
        return status.isEmpty() ? "NONE" : status.get(0);
    }

    private InvariantValidator validator(NamedParameterJdbcTemplate jdbcTemplate, MongoClient mongoClient,
                                         ReliabilityRuntimeConfig config) {
        return new InvariantValidator(
                new PostgresInvariantChecks(jdbcTemplate, catalog),
                new CrossDbInvariantChecks(jdbcTemplate, mongoClient, config, catalog)
        );
    }

    private void assumeLocalReliabilityEnabled() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_RELIABILITY_ENABLED")),
                "Set JOB1_RELIABILITY_ENABLED=true to run local Job1 repeated fault validation"
        );
    }

    // 표마다 "행 수:해시"
    record MonthDigest(String billingWork, String billingTargets, String billingCycle, String snapshots) {
        long differences(MonthDigest other) {
            return (billingWork.equals(other.billingWork) ? 0 : 1)
                    + (billingTargets.equals(other.billingTargets) ? 0 : 1)
                    + (billingCycle.equals(other.billingCycle) ? 0 : 1)
                    + (snapshots.equals(other.snapshots) ? 0 : 1);
        }
    }

    record StepRun(String stepName, String exitCode) {
    }

    // 하네스가 check_result 에 옮겨 적는 결과
    record RepeatCheck(String checkId, boolean passed, long violationCount, String detail, Map<String, Object> metrics) {
    }
}
