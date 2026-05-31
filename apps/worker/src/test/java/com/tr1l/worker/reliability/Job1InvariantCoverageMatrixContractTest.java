package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.coverage.InvariantCoverageMarkdownRenderer;
import com.tr1l.worker.reliability.coverage.InvariantCoverageMatrix;
import com.tr1l.worker.reliability.coverage.InvariantCoverageMatrixGenerator;
import com.tr1l.worker.reliability.coverage.InvariantCoverageRow;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

// Job1 시나리오와 active invariant 연결 지도
class Job1InvariantCoverageMatrixContractTest {
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final InvariantCoverageMatrixGenerator generator = new InvariantCoverageMatrixGenerator();
    private final InvariantCoverageMarkdownRenderer markdownRenderer = new InvariantCoverageMarkdownRenderer();

    @Test
    @DisplayName("Job1 시나리오와 active invariant coverage matrix 를 생성하는지 보기")
    void job1ScenariosShouldGenerateInvariantCoverageMatrix() throws Exception {
        InvariantCoverageMatrix matrix = generator.generate(
                catalog.loadScenarios(),
                catalog.loadActiveInvariants()
        );

        Path outputDir = Paths.get(
                "build",
                "job1-reliability-results",
                "coverage-matrix"
        );
        Files.createDirectories(outputDir);

        Path jsonPath = outputDir.resolve("job1-invariant-coverage-matrix.json");
        Path markdownPath = outputDir.resolve("job1-invariant-coverage-matrix.md");

        Files.writeString(
                jsonPath,
                ReliabilityObjectMappers.json().writeValueAsString(matrix),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                markdownPath,
                markdownRenderer.render(matrix),
                StandardCharsets.UTF_8
        );

        assertThat(matrix.scenarioIds())
                .contains("S-001", "S-001R", "S-003", "S-003R");
        assertThat(matrix.rows())
                .hasSize(catalog.loadActiveInvariants().size());
        assertThat(matrix.uncoveredInvariantIds()).isEmpty();
        assertThat(matrix.scenariosWithoutInvariants()).isEmpty();
        assertThat(matrix.unknownInvariantReferences()).isEmpty();

        assertThat(row(matrix, "INV-001").coveredScenarioIds())
                .contains("S-001", "S-001R", "S-003", "S-003R");
        assertThat(row(matrix, "INV-002").coveredScenarioIds())
                .contains("S-001", "S-001R", "S-003R")
                .doesNotContain("S-003");
        assertThat(row(matrix, "INV-003").coveredScenarioIds())
                .contains("S-001", "S-001R", "S-003R")
                .doesNotContain("S-003");

        assertThat(jsonPath).exists();
        assertThat(markdownPath).exists();
        assertThat(Files.readString(markdownPath))
                .contains("| invariant | category |")
                .contains("INV-001")
                .contains("S-001")
                .contains("S-003R");
    }

    private InvariantCoverageRow row(InvariantCoverageMatrix matrix, String invariantId) {
        return matrix.rows().stream()
                .filter(row -> invariantId.equals(row.invariantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Coverage row not found: " + invariantId));
    }
}
