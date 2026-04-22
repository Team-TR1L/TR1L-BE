package com.tr1l.worker.reliability;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.tr1l.worker.reliability.dataset.ExpectedSummary;
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
// 현재 타깃 디비 몽고 상태 직접 대조
// S-001 run 직후 상태 스냅샷 먼저 생성
// 그 다음 같은 cutoff 재실행 비교 순서
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Job1BaselineValidationTest {
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final JsonResultWriter resultWriter = new JsonResultWriter();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();
    private final RunStateComparisonCalculator comparisonCalculator = new RunStateComparisonCalculator();

    @Test
    @Order(1)
    @DisplayName("S-001 run이 끝난 직후의 Postgres Mongo 상태가 맞는지 보기")
    void normalRunFinalStateShouldSatisfyActiveInvariants() throws Exception {
        assumeLocalReliabilityEnabled();

        // S-001 run 직후 상태 검증
        Job1ScenarioDefinition scenario = catalog.loadScenario("S-001");
        ExpectedSummary expectedSummary = catalog.loadExpectedSummary(scenario.datasetId());
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();

        attachScenarioLabels(scenario);
        evidenceWriter.attachJson("scenario.json", scenario);
        evidenceWriter.attachJson("dataset-expected-summary.json", expectedSummary);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            // 상태 스냅샷 수집 경로
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));

            Job1StateCollector stateCollector = new Job1StateCollector(
                    jdbcTemplate,
                    mongoClient,
                    config.mongoDatabase(),
                    config.snapshotCollection()
            );

            // postgres 교차 저장소 검증 조합
            InvariantValidator validator = new InvariantValidator(
                    new PostgresInvariantChecks(jdbcTemplate, catalog),
                    new CrossDbInvariantChecks(jdbcTemplate, mongoClient, config, catalog)
            );

            Job1StateSnapshot snapshot = stateCollector.collect(LocalDate.parse(scenario.job().billingMonthDay()));
            InvariantValidationResult validationResult = validator.validate(scenario, catalog.loadActiveInvariants());

            // 결과 파일 저장
            resultWriter.write(scenario, "state-snapshot.json", snapshot);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);

            evidenceWriter.attachJson("state-snapshot.json", snapshot);
            evidenceWriter.attachJson("invariant-validation.json", validationResult);

            // D1 기대 범위 대조
            assertSnapshotWithinExpected(snapshot, expectedSummary);
            assertThat(validationResult.allPassed()).isTrue();
        }
    }

    @Test
    @Order(2)
    @DisplayName("S-001R 같은 cutoff로 다시 돌린 뒤 S-001 run 직후 상태와 count가 같은지 보기")
    void sameCutoffRerunShouldPreserveBaselineCounts() throws Exception {
        assumeLocalReliabilityEnabled();

        // 재실행 뒤 현재 상태와 S-001 run 직후 상태 비교 경로
        Job1ScenarioDefinition scenario = catalog.loadScenario("S-001R");
        Job1ScenarioDefinition referenceScenario = catalog.loadScenario("S-001");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();

        attachScenarioLabels(scenario);
        evidenceWriter.attachJson("scenario.json", scenario);

        // S-001 run 직후 상태 스냅샷 선행 생성 조건
        Path referenceSnapshotPath = resultWriter.scenarioDirectory(referenceScenario).resolve("state-snapshot.json");
        assertThat(Files.exists(referenceSnapshotPath))
                .as("Run S-001 first to produce %s", referenceSnapshotPath)
                .isTrue();

        Job1StateSnapshot referenceSnapshot =
                ReliabilityObjectMappers.json().readValue(Files.readString(referenceSnapshotPath), Job1StateSnapshot.class);

        try (MongoClient mongoClient = MongoClients.create(config.mongoUri())) {
            // 재실행 현재 상태 수집
            NamedParameterJdbcTemplate jdbcTemplate =
                    new NamedParameterJdbcTemplate(dataSourceFactory.createTargetDataSource(config));

            Job1StateCollector stateCollector = new Job1StateCollector(
                    jdbcTemplate,
                    mongoClient,
                    config.mongoDatabase(),
                    config.snapshotCollection()
            );

        // 불변 조건 검증과 S-001 run 직후 상태 비교 분리
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

            // 재실행 비교 결과 저장
            resultWriter.write(scenario, "s001-run-state-snapshot.json", referenceSnapshot);
            resultWriter.write(scenario, "current-state-snapshot.json", currentSnapshot);
            resultWriter.write(scenario, "s001-run-state-comparison.json", comparisonResult);
            resultWriter.write(scenario, "invariant-validation.json", validationResult);

            evidenceWriter.attachJson("s001-run-state-snapshot.json", referenceSnapshot);
            evidenceWriter.attachJson("current-state-snapshot.json", currentSnapshot);
            evidenceWriter.attachJson("s001-run-state-comparison.json", comparisonResult);
            evidenceWriter.attachJson("invariant-validation.json", validationResult);

            assertThat(validationResult.allPassed()).isTrue();
            assertThat(comparisonResult.matched()).isTrue();
        }
    }

    private void assumeLocalReliabilityEnabled() {
        // 로컬 준비 미완료 시 건너뜀
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

    private void assertSnapshotWithinExpected(Job1StateSnapshot snapshot, ExpectedSummary expectedSummary) {
        // 기대 범위 기준 검증
        assertThat(expectedSummary.expected().billingTargets().contains(snapshot.billingTargetsCount())).isTrue();
        assertThat(expectedSummary.expected().billingWork().contains(snapshot.billingWorkCount())).isTrue();
        assertThat(expectedSummary.expected().mongoSnapshotsAfterNormalRun().contains(snapshot.mongoSnapshotCount())).isTrue();
        assertThat(snapshot.failedCount()).isEqualTo(expectedSummary.expected().failedAfterNormalRun());
        assertThat(snapshot.staleProcessingCount()).isEqualTo(expectedSummary.expected().staleProcessingAfterRerun());
        assertThat(snapshot.duplicateBillingWorkCount()).isEqualTo(expectedSummary.expected().duplicateBillingWork());
        assertThat(snapshot.duplicateMongoSnapshotCount()).isEqualTo(expectedSummary.expected().duplicateMongoWorkId());
    }
}
