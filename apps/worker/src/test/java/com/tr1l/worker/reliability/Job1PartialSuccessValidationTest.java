package com.tr1l.worker.reliability;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
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
// partial success 상태 검증
// S-003 fault 상태 확인 뒤 S-003R 수렴 확인
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Job1PartialSuccessValidationTest {
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final JsonResultWriter resultWriter = new JsonResultWriter();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();
    private final RunStateComparisonCalculator comparisonCalculator = new RunStateComparisonCalculator();

    @Test
    @Order(1)
    @DisplayName("S-003 Mongo 저장만 남고 PG 상태는 TARGET 으로 돌아간 흔적 보기")
    void afterMongoSaveFailureShouldLeaveTargetRowsWithSnapshots() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-003");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();

        attachScenarioLabels(scenario);
        evidenceWriter.attachJson("scenario.json", scenario);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));

            Job1StateCollector stateCollector = new Job1StateCollector(
                    jdbcTemplate,
                    mongoClient,
                    config.mongoDatabase(),
                    config.snapshotCollection()
            );

            InvariantValidator validator = new InvariantValidator(
                    new PostgresInvariantChecks(jdbcTemplate, catalog),
                    new CrossDbInvariantChecks(jdbcTemplate, mongoClient, config, catalog)
            );

            Job1StateSnapshot snapshot = stateCollector.collect(LocalDate.parse(scenario.job().billingMonthDay()));
            InvariantValidationResult validationResult = validator.validate(scenario, catalog.loadActiveInvariants());

            resultWriter.write(scenario, "fault-state-snapshot.json", snapshot);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);

            evidenceWriter.attachJson("fault-state-snapshot.json", snapshot);
            evidenceWriter.attachJson("invariant-validation.json", validationResult);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(snapshot.targetCount()).isEqualTo(snapshot.billingWorkCount());
            assertThat(snapshot.targetWithSnapshotCount()).isGreaterThan(0);
            assertThat(snapshot.mongoSnapshotCount()).isGreaterThan(snapshot.calculatedCount());
            assertThat(snapshot.sampleTargetWorkIdsWithSnapshot()).isNotEmpty();
            assertThat(snapshot.duplicateBillingWorkCount()).isZero();
            assertThat(snapshot.duplicateMongoSnapshotCount()).isZero();
        }
    }

    @Test
    @Order(2)
    @DisplayName("S-003R 다시 돌린 뒤 S-001 run 직후 상태로 수렴하는지 보기")
    void rerunAfterPartialSuccessShouldConvergeToS001RunState() throws Exception {
        assumeLocalReliabilityEnabled();

        Job1ScenarioDefinition scenario = catalog.loadScenario("S-003R");
        Job1ScenarioDefinition referenceScenario = catalog.loadScenario("S-001");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();

        attachScenarioLabels(scenario);
        evidenceWriter.attachJson("scenario.json", scenario);

        Path referenceSnapshotPath = resultWriter.scenarioDirectory(referenceScenario).resolve("state-snapshot.json");
        assertThat(Files.exists(referenceSnapshotPath))
                .as("Run S-001 first to produce %s", referenceSnapshotPath)
                .isTrue();

        Job1StateSnapshot referenceSnapshot =
                ReliabilityObjectMappers.json().readValue(Files.readString(referenceSnapshotPath), Job1StateSnapshot.class);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));

            Job1StateCollector stateCollector = new Job1StateCollector(
                    jdbcTemplate,
                    mongoClient,
                    config.mongoDatabase(),
                    config.snapshotCollection()
            );

            InvariantValidator validator = new InvariantValidator(
                    new PostgresInvariantChecks(jdbcTemplate, catalog),
                    new CrossDbInvariantChecks(jdbcTemplate, mongoClient, config, catalog)
            );

            Job1StateSnapshot currentSnapshot = stateCollector.collect(LocalDate.parse(scenario.job().billingMonthDay()));
            InvariantValidationResult validationResult = validator.validate(scenario, catalog.loadActiveInvariants());
            RunStateComparisonResult comparisonResult = comparisonCalculator.compare(
                    scenario.id(),
                    scenario.compareWithBaseline().baselineScenarioId(),
                    referenceSnapshot,
                    currentSnapshot,
                    scenario.compareWithBaseline().metrics()
            );

            resultWriter.write(scenario, "s001-run-state-snapshot.json", referenceSnapshot);
            resultWriter.write(scenario, "current-state-snapshot.json", currentSnapshot);
            resultWriter.write(scenario, "s001-run-state-comparison.json", comparisonResult);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);

            evidenceWriter.attachJson("s001-run-state-snapshot.json", referenceSnapshot);
            evidenceWriter.attachJson("current-state-snapshot.json", currentSnapshot);
            evidenceWriter.attachJson("s001-run-state-comparison.json", comparisonResult);
            evidenceWriter.attachJson("invariant-validation.json", validationResult);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(currentSnapshot.targetWithSnapshotCount()).isZero();
            assertThat(currentSnapshot.processingCount()).isZero();
            assertThat(currentSnapshot.processingWithSnapshotCount()).isZero();
            assertThat(comparisonResult.matched()).isTrue();
        }
    }

    private void assumeLocalReliabilityEnabled() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_RELIABILITY_ENABLED")),
                "Set JOB1_RELIABILITY_ENABLED=true to run local Job1 reliability validation"
        );
    }

    private void attachScenarioLabels(Job1ScenarioDefinition scenario) {
        Allure.label("epic", scenario.allure().epic());
        Allure.label("feature", scenario.allure().feature());
        Allure.label("story", scenario.allure().story());
    }
}
