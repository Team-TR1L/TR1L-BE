package com.tr1l.worker.reliability;

import com.fasterxml.jackson.databind.JsonNode;
import com.tr1l.worker.reliability.invariant.InvariantDefinition;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import com.tr1l.worker.reliability.support.ReliabilityResourceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 사람이 검토한 승격 기록 검증
class Job1InvariantHumanReviewContractTest {
    private static final String REVIEW_PATH = "reliability/invariants/reviewed/J1S3-I7-review.json";

    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();

    @Test
    @DisplayName("J1S3-I7 검토 기록이 INV-004 active 파일과 이어지는지 보기")
    void reviewedInvariantShouldReferencePromotedActiveInvariant() throws Exception {
        // generated 후보에서 active 승격까지 연결 확인
        JsonNode review = ReliabilityObjectMappers.json().readTree(loader.loadText(REVIEW_PATH));
        JsonNode promotionTarget = review.path("promotionTarget");
        String activeInvariantFile = promotionTarget.path("activeInvariantFile").asText();
        String checkRef = promotionTarget.path("checkRef").asText();

        InvariantDefinition promoted = loader.loadJson(activeInvariantFile, InvariantDefinition.class);

        assertThat(review.path("generatedInvariantId").asText()).isEqualTo("J1S3-I7");
        assertThat(review.path("reviewStatus").asText()).isEqualTo("approved");
        assertThat(review.path("scoreSummary").path("totalScore").asInt()).isGreaterThanOrEqualTo(85);
        assertThat(promotionTarget.path("activeInvariantId").asText()).isEqualTo("INV-004");
        assertThat(catalog.resourceExists(activeInvariantFile)).isTrue();
        assertThat(catalog.resourceExists(checkRef)).isTrue();
        assertThat(promoted.id()).isEqualTo("INV-004");
        assertThat(promoted.checkRef()).isEqualTo(checkRef);
    }
}
