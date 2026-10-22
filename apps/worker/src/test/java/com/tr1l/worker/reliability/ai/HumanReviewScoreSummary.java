package com.tr1l.worker.reliability.ai;

// 사람 검토 전 점수 요약
public record HumanReviewScoreSummary(
        int totalScore,
        int formatScore,
        int sqlExecutableScore,
        int semanticAlignmentScore,
        int scenarioLinkageScore,
        int noveltyScore,
        boolean sqlExecutable,
        boolean semanticallyAligned,
        boolean scenarioLinked,
        boolean novelAgainstActive
) {
}
