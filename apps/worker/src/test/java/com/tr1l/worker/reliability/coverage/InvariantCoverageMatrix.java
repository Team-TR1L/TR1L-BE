package com.tr1l.worker.reliability.coverage;

import java.util.List;

// 시나리오와 불변 조건 연결 결과
public record InvariantCoverageMatrix(
        List<String> scenarioIds,
        int scenarioCount,
        int invariantCount,
        List<InvariantCoverageRow> rows,
        List<String> uncoveredInvariantIds,
        List<String> scenariosWithoutInvariants,
        List<UnknownInvariantReference> unknownInvariantReferences
) {
    public boolean hasUncoveredInvariant() {
        return !uncoveredInvariantIds.isEmpty();
    }

    public boolean hasInvalidReference() {
        return !unknownInvariantReferences.isEmpty();
    }

    public record UnknownInvariantReference(
            String scenarioId,
            String invariantId
    ) {
    }
}
