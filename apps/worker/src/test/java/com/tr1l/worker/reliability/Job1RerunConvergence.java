package com.tr1l.worker.reliability;

import com.mongodb.client.MongoClient;
import com.tr1l.worker.reliability.content.ContentValidation;
import com.tr1l.worker.reliability.content.ReferenceAmountComparison;
import com.tr1l.worker.reliability.content.SnapshotHashComparison;
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
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// 재실행이 끝난 뒤 S-001 run 직후 상태와 내용으로 돌아왔는지 보는 공통 검증
// S-010R, S-002R, S-011 이 같이 쓴다
final class Job1RerunConvergence {
    private final ReliabilityResourceCatalog catalog;
    private final JsonResultWriter resultWriter;
    private final AllureEvidenceWriter evidenceWriter;
    private final RunStateComparisonCalculator comparisonCalculator = new RunStateComparisonCalculator();

    Job1RerunConvergence(ReliabilityResourceCatalog catalog, JsonResultWriter resultWriter,
                         AllureEvidenceWriter evidenceWriter) {
        this.catalog = catalog;
        this.resultWriter = resultWriter;
        this.evidenceWriter = evidenceWriter;
    }

    // 결과 파일을 남기고 invariant, S-001 비교, C5, C6 을 모두 확인한다
    void verify(Job1ScenarioDefinition scenario, NamedParameterJdbcTemplate jdbcTemplate, MongoClient mongoClient,
                ReliabilityRuntimeConfig config) throws Exception {
        Job1ScenarioDefinition referenceScenario = catalog.loadScenario("S-001");
        Path referenceSnapshotPath = resultWriter.scenarioDirectory(referenceScenario).resolve("state-snapshot.json");
        assertThat(Files.exists(referenceSnapshotPath))
                .as("Run S-001 first to produce %s", referenceSnapshotPath)
                .isTrue();
        Job1StateSnapshot referenceSnapshot =
                ReliabilityObjectMappers.json().readValue(Files.readString(referenceSnapshotPath), Job1StateSnapshot.class);

        LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());
        Job1StateSnapshot currentSnapshot =
                new Job1StateCollector(jdbcTemplate, mongoClient, config.mongoDatabase(), config.snapshotCollection())
                        .collect(billingMonthDay);
        InvariantValidationResult validationResult = new InvariantValidator(
                new PostgresInvariantChecks(jdbcTemplate, catalog),
                new CrossDbInvariantChecks(jdbcTemplate, mongoClient, config, catalog)
        ).validate(scenario, catalog.loadActiveInvariants());
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
