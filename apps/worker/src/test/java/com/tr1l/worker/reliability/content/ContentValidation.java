package com.tr1l.worker.reliability.content;

import com.mongodb.client.MongoClient;
import com.tr1l.worker.reliability.scenario.Job1ScenarioDefinition;
import com.tr1l.worker.reliability.support.AllureEvidenceWriter;
import com.tr1l.worker.reliability.support.JsonResultWriter;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

// snapshot 내용 검증 묶음
// C5 S-001 기준 snapshot 해시 비교
// C6 참조 계산 금액 비교
public final class ContentValidation {
    public static final String BASELINE_HASHES = "snapshot-hashes.json";
    public static final String HASH_COMPARISON = "snapshot-hash-comparison.json";
    public static final String REFERENCE_COMPARISON = "reference-amount-comparison.json";

    private final SnapshotHashCollector hashCollector;
    private final BillingAmountCollector amountCollector;
    private final SnapshotHashComparator hashComparator = new SnapshotHashComparator();
    private final ReferenceAmountComparator amountComparator = new ReferenceAmountComparator();
    private final JsonResultWriter resultWriter;
    private final AllureEvidenceWriter evidenceWriter;

    public ContentValidation(
            NamedParameterJdbcTemplate jdbcTemplate,
            MongoClient mongoClient,
            ReliabilityRuntimeConfig config,
            ReliabilityResourceCatalog catalog,
            JsonResultWriter resultWriter,
            AllureEvidenceWriter evidenceWriter
    ) {
        this.hashCollector = new SnapshotHashCollector(mongoClient, config.mongoDatabase(), config.snapshotCollection());
        this.amountCollector = new BillingAmountCollector(
                jdbcTemplate, mongoClient, config.mongoDatabase(), config.snapshotCollection(), catalog);
        this.resultWriter = resultWriter;
        this.evidenceWriter = evidenceWriter;
    }

    // S-001 해시 기준 저장
    public SnapshotHashes recordBaselineHashes(Job1ScenarioDefinition scenario, LocalDate billingMonthDay) {
        SnapshotHashes hashes = hashCollector.collect(billingMonthDay);
        resultWriter.write(scenario, BASELINE_HASHES, hashes);
        return hashes;
    }

    // 재실행 뒤 S-001 해시와 비교
    public SnapshotHashComparison compareWithBaselineHashes(
            Job1ScenarioDefinition scenario,
            Job1ScenarioDefinition baselineScenario,
            LocalDate billingMonthDay
    ) throws IOException {
        Path baselinePath = resultWriter.scenarioDirectory(baselineScenario).resolve(BASELINE_HASHES);
        if (!Files.exists(baselinePath)) {
            throw new IllegalStateException("Run " + baselineScenario.id() + " first to produce " + baselinePath);
        }

        SnapshotHashes baseline = ReliabilityObjectMappers.json().readValue(Files.readString(baselinePath), SnapshotHashes.class);
        SnapshotHashComparison comparison = hashComparator.compare(
                scenario.id(), baselineScenario.id(), baseline, hashCollector.collect(billingMonthDay));

        resultWriter.write(scenario, HASH_COMPARISON, comparison);
        evidenceWriter.attachJson(HASH_COMPARISON, comparison);
        return comparison;
    }

    // 참조 계산 금액과 비교
    public ReferenceAmountComparison compareReferenceAmounts(Job1ScenarioDefinition scenario, LocalDate billingMonthDay) {
        ReferenceAmountComparison comparison = amountComparator.compare(
                scenario.id(), amountCollector.expected(billingMonthDay), amountCollector.actual(billingMonthDay));

        resultWriter.write(scenario, REFERENCE_COMPARISON, comparison);
        evidenceWriter.attachJson(REFERENCE_COMPARISON, comparison);
        return comparison;
    }
}
