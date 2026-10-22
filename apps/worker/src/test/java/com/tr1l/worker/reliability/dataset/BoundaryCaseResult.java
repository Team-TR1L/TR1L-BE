package com.tr1l.worker.reliability.dataset;

import java.time.Instant;
import java.util.List;

// 경계값 경우별 결과
public record BoundaryCaseResult(
        String datasetId,
        Instant checkedAt,
        List<Case> cases
) {
    public List<Case> failedCases() {
        return cases.stream().filter(item -> !item.passed()).toList();
    }

    public long needsDecisionCount() {
        return cases.stream().filter(item -> "needs-decision".equals(item.decision())).count();
    }

    // 경우 하나. 사용자 10명 모두 기대와 같아야 통과
    public record Case(
            String code,
            String decision,
            String note,
            int users,
            boolean passed,
            List<String> differences
    ) {
    }
}
