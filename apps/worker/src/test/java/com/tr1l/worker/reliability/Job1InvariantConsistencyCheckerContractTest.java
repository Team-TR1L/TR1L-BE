package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.ai.AiArtifactWriter;
import com.tr1l.worker.reliability.ai.GeneratedInvariantCandidate;
import com.tr1l.worker.reliability.ai.GeneratedInvariantConsistencyChecker;
import com.tr1l.worker.reliability.ai.GeneratedInvariantParser;
import com.tr1l.worker.reliability.ai.InvariantConsistencyDecision;
import com.tr1l.worker.reliability.ai.InvariantConsistencyReport;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityResourceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// consistency checker 계약 검증
class Job1InvariantConsistencyCheckerContractTest {
    private static final String SAMPLE_RESPONSE_PATH =
            "reliability/invariants/generated/job1-invariant-candidates.sample.json";
    private static final String CHECKER_PROMPT_PATH =
            "reliability/ai/prompts/job1-invariant-consistency-checker-system.txt";
    private static final String CHECKER_RUN_NAME = "consistency-checker";

    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();
    private final GeneratedInvariantParser parser = new GeneratedInvariantParser();
    private final GeneratedInvariantConsistencyChecker checker = new GeneratedInvariantConsistencyChecker();
    private final AiArtifactWriter artifactWriter = new AiArtifactWriter();

    @Test
    @DisplayName("consistency checker 프롬프트가 별도 검토자 역할과 출력 규칙을 담는지 보기")
    void consistencyCheckerPromptShouldContainReviewerRules() {
        // LLM checker 지시문 계약 확인
        String prompt = loader.loadText(CHECKER_PROMPT_PATH);

        assertThat(prompt)
                .contains("다른 LLM 이 생성한 invariant 후보")
                .contains("PASS WARN FAIL")
                .contains("PROCESSING 상태")
                .contains("cross-db 검증");
    }

    @Test
    @DisplayName("generated 후보를 PASS WARN FAIL consistency 리포트로 나누는지 보기")
    void generatedCandidatesShouldBeCheckedWithConsistencyReport() {
        // generated 후보 별도 검토 결과 저장
        List<GeneratedInvariantCandidate> candidates = parser.loadFromResource(SAMPLE_RESPONSE_PATH);
        InvariantConsistencyReport report = checker.check(candidates, catalog.loadActiveInvariants());

        artifactWriter.writeJson(CHECKER_RUN_NAME, "job1-invariant-consistency-report.json", report);

        assertThat(report.totalCandidates()).isEqualTo(4);
        assertThat(report.passCount()).isZero();
        assertThat(report.warnCount()).isEqualTo(4);
        assertThat(report.failCount()).isZero();
        assertThat(report.reviews()).allSatisfy(review ->
                assertThat(review.decision()).isEqualTo(InvariantConsistencyDecision.WARN)
        );
        assertThat(report.reviews().get(0).findings())
                .anySatisfy(finding -> assertThat(finding.code()).isEqualTo("ACTIVE_DUPLICATE_REVIEW"));
    }

    @Test
    @DisplayName("SQL 의미가 맞지 않는 후보는 consistency checker 에서 FAIL 로 막는지 보기")
    void invalidSqlMeaningShouldFailConsistencyCheck() {
        // 실패 후보 차단 확인
        GeneratedInvariantCandidate invalid = new GeneratedInvariantCandidate(
                "BAD-001",
                "count_consistency",
                "billing_work 수가 target 수와 같아야 한다",
                "always",
                "SELECT 1",
                "잘못된 SQL 생성",
                "critical"
        );

        InvariantConsistencyReport report = checker.check(List.of(invalid), catalog.loadActiveInvariants());

        assertThat(report.totalCandidates()).isEqualTo(1);
        assertThat(report.passCount()).isZero();
        assertThat(report.warnCount()).isZero();
        assertThat(report.failCount()).isEqualTo(1);
        assertThat(report.reviews().get(0).findings())
                .extracting("code")
                .contains("SQL_SHAPE_INVALID", "CATEGORY_SQL_MISMATCH");
    }
}
