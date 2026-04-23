package com.tr1l.worker.reliability.ai;

// active 승격 대상
public record HumanReviewPromotionTarget(
        String activeInvariantId,
        String activeInvariantFile,
        String checkRef
) {
}
