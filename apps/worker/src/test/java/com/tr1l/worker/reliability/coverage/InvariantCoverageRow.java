package com.tr1l.worker.reliability.coverage;

import java.util.List;

// 불변 조건 하나가 어떤 시나리오에서 검증되는지 표현
public record InvariantCoverageRow(
        String invariantId,
        String title,
        String category,
        String scope,
        List<String> coveredScenarioIds,
        int coverageCount
) {
    public boolean coveredBy(String scenarioId) {
        return coveredScenarioIds.contains(scenarioId);
    }
}
