package com.tr1l.worker.reliability.ai;

import java.util.List;

// generated 후보 사람 검토 기록
public record HumanReviewRecord(
        String generatedInvariantId,
        String sourceRunName,
        String sourceResponseId,
        String reviewStatus,
        String reviewedBy,
        String reviewedAt,
        HumanReviewScoreSummary scoreSummary,
        String decisionReason,
        List<String> reviewNotes,
        HumanReviewPromotionTarget promotionTarget
) {
}
