package com.tr1l.worker.reliability;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.tr1l.worker.reliability.content.ContentValidation;
import com.tr1l.worker.reliability.content.ReferenceAmountComparison;
import com.tr1l.worker.reliability.content.SnapshotHashComparison;
import com.tr1l.worker.reliability.fault.FaultPointExpectation;
import com.tr1l.worker.reliability.fault.FaultPoints;
import com.tr1l.worker.reliability.invariant.CrossDbInvariantChecks;
import com.tr1l.worker.reliability.invariant.InvariantValidationResult;
import com.tr1l.worker.reliability.invariant.InvariantValidator;
import com.tr1l.worker.reliability.invariant.Job1StateCollector;
import com.tr1l.worker.reliability.invariant.Job1StateSnapshot;
import com.tr1l.worker.reliability.invariant.PostgresInvariantChecks;
import com.tr1l.worker.reliability.invariant.RunStateComparisonCalculator;
import com.tr1l.worker.reliability.invariant.RunStateComparisonResult;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-reliability")
// 로컬 환경 전용 검증
// 장애 행렬 한 칸: S-010 장애 직후 상태 확인 뒤 S-010R 수렴 확인
// 지점은 JOB1_FAULT_POINT 로 받는다 (하네스가 worker 에 넘긴 값과 같다)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Job1FaultMatrixValidationTest {
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final JsonResultWriter resultWriter = new JsonResultWriter();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();
    private final RunStateComparisonCalculator comparisonCalculator = new RunStateComparisonCalculator();
    private final FaultPointExpectation expectation = new FaultPointExpectation();

    @Test
    @Order(1)
    @DisplayName("S-010 장애 직후 지점별 예상 상태와 중복 없음 보기")
    void afterFaultShouldMatchExpectedStateOfPoint() throws Exception {
        assumeLocalReliabilityEnabled();

        String point = System.getenv("JOB1_FAULT_POINT");
        assertThat(point).as("JOB1_FAULT_POINT").isNotBlank();
        FaultPoints.Point definition = catalog.loadFaultPoints().points().get(point);
        assertThat(definition).as("fault point %s", point).isNotNull();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-010");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story() + " " + point + " " + System.getenv("JOB1_FAULT_TIMING"));
        evidenceWriter.attachJson("scenario.json", scenario);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));

            Job1StateSnapshot snapshot = collector(jdbcTemplate, mongoClient, config)
                    .collect(LocalDate.parse(scenario.job().billingMonthDay()));
            InvariantValidationResult validationResult = validator(jdbcTemplate, mongoClient, config)
                    .validate(scenario, catalog.loadActiveInvariants());
            FaultPointExpectation.Result expected =
                    expectation.evaluate(point, definition.expect(), snapshot, targetUsers(config));

            resultWriter.write(scenario, "fault-state-snapshot.json", snapshot);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);
            resultWriter.write(scenario, "fault-expectation.json", expected);
            evidenceWriter.attachJson("fault-state-snapshot.json", snapshot);
            evidenceWriter.attachJson("fault-expectation.json", expected);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(expected.matched()).as("fault expectation %s", expected).isTrue();
        }
    }

    @Test
    @Order(2)
    @DisplayName("S-010R 장애 뒤 같은 cutoff 로 다시 돌리면 S-001 run 직후 상태와 내용으로 돌아오는지 보기")
    void rerunAfterFaultShouldConvergeToS001RunState() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-010R");
        Job1ScenarioDefinition referenceScenario = catalog.loadScenario("S-001");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        Allure.label("story", scenario.allure().story() + " " + System.getenv("JOB1_FAULT_POINT"));

        Path referenceSnapshotPath = resultWriter.scenarioDirectory(referenceScenario).resolve("state-snapshot.json");
        assertThat(Files.exists(referenceSnapshotPath))
                .as("Run S-001 first to produce %s", referenceSnapshotPath)
                .isTrue();
        Job1StateSnapshot referenceSnapshot =
                ReliabilityObjectMappers.json().readValue(Files.readString(referenceSnapshotPath), Job1StateSnapshot.class);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));
            LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());

            Job1StateSnapshot currentSnapshot = collector(jdbcTemplate, mongoClient, config).collect(billingMonthDay);
            InvariantValidationResult validationResult = validator(jdbcTemplate, mongoClient, config)
                    .validate(scenario, catalog.loadActiveInvariants());
            RunStateComparisonResult comparisonResult = comparisonCalculator.compare(
                    scenario.id(),
                    referenceScenario.id(),
                    referenceSnapshot,
                    currentSnapshot,
                    scenario.compareWithBaseline().metrics()
            );

            resultWriter.write(scenario, "current-state-snapshot.json", currentSnapshot);
            resultWriter.write(scenario, "s001-run-state-comparison.json", comparisonResult);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);
            evidenceWriter.attachJson("s001-run-state-comparison.json", comparisonResult);
            evidenceWriter.attachJson("invariant-validation.json", validationResult);

            // snapshot 내용 비교 (C5, C6)
            ContentValidation contentValidation =
                    new ContentValidation(jdbcTemplate, mongoClient, config, catalog, resultWriter, evidenceWriter);
            SnapshotHashComparison hashComparison =
                    contentValidation.compareWithBaselineHashes(scenario, referenceScenario, billingMonthDay);
            ReferenceAmountComparison amountComparison =
                    contentValidation.compareReferenceAmounts(scenario, billingMonthDay);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(currentSnapshot.targetWithSnapshotCount()).isZero();
            assertThat(currentSnapshot.processingCount()).isZero();
            assertThat(comparisonResult.matched()).isTrue();
            assertThat(hashComparison.matched()).as("C5 snapshot hash %s", hashComparison).isTrue();
            assertThat(amountComparison.matched()).as("C6 reference amount %s", amountComparison).isTrue();
        }
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

    private Job1StateCollector collector(NamedParameterJdbcTemplate jdbcTemplate, MongoClient mongoClient,
                                         ReliabilityRuntimeConfig config) {
        return new Job1StateCollector(jdbcTemplate, mongoClient, config.mongoDatabase(), config.snapshotCollection());
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
                "Set JOB1_RELIABILITY_ENABLED=true to run local Job1 fault matrix validation"
        );
    }
}
