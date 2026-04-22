package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.ai.AiArtifactWriter;
import com.tr1l.worker.reliability.ai.GeneratedInvariantBatchEvaluation;
import com.tr1l.worker.reliability.ai.GeneratedInvariantEvaluation;
import com.tr1l.worker.reliability.ai.GeneratedInvariantParser;
import com.tr1l.worker.reliability.ai.GeneratedInvariantPromotionDecision;
import com.tr1l.worker.reliability.ai.GeneratedInvariantScorer;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// generated 후보 점수화 계약 검증
class Job1AiInvariantScoringContractTest {
    private static final String SAMPLE_RESPONSE_PATH =
            "reliability/invariants/generated/job1-invariant-candidates.sample.json";
    private static final String SCORE_RUN_NAME = "sample-generated-invariant-score";

    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final GeneratedInvariantParser parser = new GeneratedInvariantParser();
    private final GeneratedInvariantScorer scorer = new GeneratedInvariantScorer();
    private final AiArtifactWriter artifactWriter = new AiArtifactWriter();

    @Test
    @DisplayName("generated 후보 점수화 기준이 active 승격 후보와 중복 후보를 나누는지 보기")
    void generatedCandidatesShouldBeScoredWithPromotionDecision() {
        // 점수화 결과 검증
        GeneratedInvariantBatchEvaluation evaluation = scorer.evaluate(
                parser.loadFromResource(SAMPLE_RESPONSE_PATH),
                catalog.loadActiveInvariants()
        );

        artifactWriter.writeJson(SCORE_RUN_NAME, "generated-candidate-evaluation.json", evaluation);

        Map<String, GeneratedInvariantEvaluation> byId = evaluation.evaluations().stream()
                .collect(Collectors.toMap(GeneratedInvariantEvaluation::invariantId, Function.identity()));

        assertThat(evaluation.totalCandidates()).isEqualTo(4);
        assertThat(evaluation.activeCandidateCount()).isEqualTo(1);
        assertThat(evaluation.reviewRequiredCount()).isEqualTo(3);
        assertThat(evaluation.rejectCount()).isEqualTo(0);
        assertThat(evaluation.averageScore()).isGreaterThanOrEqualTo(85d);

        assertThat(byId.get("INV-101").decision()).isEqualTo(GeneratedInvariantPromotionDecision.ACTIVE_CANDIDATE);
        assertThat(byId.get("INV-101").novelAgainstActive()).isTrue();
        assertThat(byId.get("INV-102").decision()).isEqualTo(GeneratedInvariantPromotionDecision.REVIEW_REQUIRED);
        assertThat(byId.get("INV-102").matchedActiveInvariantId()).isEqualTo("INV-001");
        assertThat(byId.get("INV-103").matchedActiveInvariantId()).isEqualTo("INV-002");
        assertThat(byId.get("INV-104").matchedActiveInvariantId()).isEqualTo("INV-003");
    }
}
