package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.dataset.DatasetDistributionCheck;
import com.tr1l.worker.reliability.dataset.DatasetDistributionResult;
import com.tr1l.worker.reliability.dataset.Job1DatasetDefinition;
import com.tr1l.worker.reliability.support.AllureEvidenceWriter;
import com.tr1l.worker.reliability.support.ReliabilityDataSourceFactory;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityRuntimeConfig;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-reliability")
// 로컬 환경 전용 검증
// 시드 적재 직후 main 디비 분포가 dataset.yml 과 같은지 대조
class Job1DatasetDistributionValidationTest {
    private static final String DISTRIBUTION_SQL = "reliability/checks/dataset/main-distribution.sql";

    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final ReliabilityDataSourceFactory dataSourceFactory = new ReliabilityDataSourceFactory();
    private final DatasetDistributionCheck distributionCheck = new DatasetDistributionCheck();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();

    @Test
    @DisplayName("D1 시드를 넣은 뒤 main 디비 분포가 dataset.yml 과 같은지 보기")
    void d1SeedShouldMatchDatasetDistribution() throws Exception {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_RELIABILITY_ENABLED")),
                "Set JOB1_RELIABILITY_ENABLED=true to run local dataset distribution validation"
        );

        Job1DatasetDefinition dataset = catalog.loadDataset("D1-rerun-mvp");
        ReliabilityRuntimeConfig config = ReliabilityRuntimeConfig.fromEnvironment();
        NamedParameterJdbcTemplate jdbcTemplate =
                new NamedParameterJdbcTemplate(dataSourceFactory.createMainDataSource(config));

        // 기준 월 범위
        LocalDate monthStart = LocalDate.parse(dataset.billingMonthDay());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("monthStart", monthStart)
                .addValue("monthEnd", monthStart.plusMonths(1).minusDays(1))
                .addValue("yearMonth", dataset.billingYearMonth());

        Map<String, Long> actual = new HashMap<>();
        jdbcTemplate.query(catalog.loadCheckText(DISTRIBUTION_SQL), params,
                rs -> {
                    actual.put(rs.getString("metric"), rs.getLong("actual"));
                });

        DatasetDistributionResult result = distributionCheck.compare(dataset, actual);

        // 결과 파일 저장
        Path output = Paths.get("build", "job1-reliability-results", "dataset", dataset.id() + "-distribution.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, ReliabilityObjectMappers.json().writeValueAsString(result), StandardCharsets.UTF_8);
        evidenceWriter.attachJson("dataset-distribution.json", result);

        assertThat(result.mismatchedRows())
                .as("dataset.yml 과 다른 분포 항목")
                .isEmpty();
    }
}
