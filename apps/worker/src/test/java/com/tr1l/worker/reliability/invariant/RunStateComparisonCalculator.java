package com.tr1l.worker.reliability.invariant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// S-001 run 직후 상태와 현재 상태 비교기
public final class RunStateComparisonCalculator {

    public RunStateComparisonResult compare(
            String scenarioId,
            String referenceScenarioId,
            Job1StateSnapshot referenceSnapshot,
            Job1StateSnapshot currentSnapshot,
            List<String> metrics
    ) {
        // S-001 run 직후 수치 보관 맵
        Map<String, Long> referenceMetrics = new LinkedHashMap<>();
        // 현재 수치 보관 맵
        Map<String, Long> currentMetrics = new LinkedHashMap<>();
        // 달라진 항목 모음
        List<RunStateComparisonResult.Difference> differences = new ArrayList<>();

        for (String metric : metrics) {
            long referenceValue = referenceSnapshot.metricValue(metric);
            long currentValue = currentSnapshot.metricValue(metric);

            referenceMetrics.put(metric, referenceValue);
            currentMetrics.put(metric, currentValue);

            if (referenceValue != currentValue) {
                differences.add(new RunStateComparisonResult.Difference(metric, referenceValue, currentValue));
            }
        }

        return new RunStateComparisonResult(
                scenarioId,
                referenceScenarioId,
                Instant.now(),
                differences.isEmpty(),
                referenceMetrics,
                currentMetrics,
                differences
        );
    }
}
