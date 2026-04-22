package com.tr1l.worker.reliability.ai;

// 후보 단건 평가 결과
public record GeneratedInvariantEvaluation(
        String invariantId,
        int formatScore,
        int sqlExecutableScore,
        int semanticAlignmentScore,
        int scenarioLinkageScore,
        int noveltyScore,
        int totalScore,
        boolean sqlExecutable,
        boolean semanticallyAligned,
        boolean scenarioLinked,
        boolean novelAgainstActive,
        String matchedActiveInvariantId,
        double noveltySimilarity,
        GeneratedInvariantPromotionDecision decision
) {
}
