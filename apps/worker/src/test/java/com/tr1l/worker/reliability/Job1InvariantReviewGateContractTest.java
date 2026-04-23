package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.ai.AiArtifactWriter;
import com.tr1l.worker.reliability.ai.GeneratedInvariantReviewGate;
import com.tr1l.worker.reliability.ai.HumanReviewRecord;
import com.tr1l.worker.reliability.ai.ReviewGateResult;
import com.tr1l.worker.reliability.invariant.InvariantDefinition;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityResourceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// human review gate 계약 검증
class Job1InvariantReviewGateContractTest {
    private static final String REVIEW_PATH = "reliability/invariants/reviewed/J1S3-I7-review.json";
    private static final String GATE_RUN_NAME = "review-gate";

    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final GeneratedInvariantReviewGate reviewGate = new GeneratedInvariantReviewGate();
    private final AiArtifactWriter artifactWriter = new AiArtifactWriter();

    @Test
    @DisplayName("approved review 만 INV-004 active 승격 gate 를 통과하는지 보기")
    void approvedHumanReviewShouldPassPromotionGate() {
        // 실제 review 파일 gate 판정
        HumanReviewRecord review = loader.loadJson(REVIEW_PATH, HumanReviewRecord.class);
        InvariantDefinition promoted = loader.loadJson(
                review.promotionTarget().activeInvariantFile(),
                InvariantDefinition.class
        );

        ReviewGateResult result = reviewGate.evaluate(review, promoted, catalog);
        artifactWriter.writeJson(GATE_RUN_NAME, "J1S3-I7-review-gate-result.json", result);

        assertThat(result.passed()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).containsExactly("scenarioLinked false 를 사람 검토 메모로 보완");
        assertThat(result.activeInvariantId()).isEqualTo("INV-004");
    }

    @Test
    @DisplayName("approved 가 아닌 review 는 active 승격 gate 에서 막히는지 보기")
    void nonApprovedHumanReviewShouldBeBlocked() {
        // 상태값 gate 차단 확인
        HumanReviewRecord review = loader.loadJson(REVIEW_PATH, HumanReviewRecord.class);
        HumanReviewRecord rejectedReview = new HumanReviewRecord(
                review.generatedInvariantId(),
                review.sourceRunName(),
                review.sourceResponseId(),
                "needs_revision",
                review.reviewedBy(),
                review.reviewedAt(),
                review.scoreSummary(),
                review.decisionReason(),
                review.reviewNotes(),
                review.promotionTarget()
        );
        InvariantDefinition promoted = loader.loadJson(
                review.promotionTarget().activeInvariantFile(),
                InvariantDefinition.class
        );

        ReviewGateResult result = reviewGate.evaluate(rejectedReview, promoted, catalog);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockers()).contains("reviewStatus 가 approved 가 아님");
    }
}
