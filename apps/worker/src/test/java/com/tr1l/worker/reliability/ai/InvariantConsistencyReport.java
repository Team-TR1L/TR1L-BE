package com.tr1l.worker.reliability.ai;

import java.util.List;

// consistency checker 전체 리포트
public record InvariantConsistencyReport(
        List<InvariantConsistencyReview> reviews,
        int totalCandidates,
        int passCount,
        int warnCount,
        int failCount
) {
}
