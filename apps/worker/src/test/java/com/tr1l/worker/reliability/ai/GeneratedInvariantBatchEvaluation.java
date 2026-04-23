package com.tr1l.worker.reliability.ai;

import java.util.List;

// 후보 묶음 평가 요약
public record GeneratedInvariantBatchEvaluation(
        List<GeneratedInvariantEvaluation> evaluations,
        int totalCandidates,
        int activeCandidateCount,
        int reviewRequiredCount,
        int rejectCount,
        double averageScore
) {
}
