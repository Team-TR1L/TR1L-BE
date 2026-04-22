package com.tr1l.worker.reliability.invariant;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// S-001 run 직후 상태 비교 결과
public record RunStateComparisonResult(
        String scenarioId,
        String referenceScenarioId,
        Instant comparedAt,
        boolean matched,
        Map<String, Long> referenceMetrics,
        Map<String, Long> currentMetrics,
        List<Difference> differences
) {
    // 달라진 지표 항목
    public record Difference(
            String metric,
            long referenceValue,
            long currentValue
    ) {
    }
}
