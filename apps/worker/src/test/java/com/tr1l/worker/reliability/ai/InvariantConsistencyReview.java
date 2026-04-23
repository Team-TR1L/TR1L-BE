package com.tr1l.worker.reliability.ai;

import java.util.List;

// invariant 단건 consistency 검토 결과
public record InvariantConsistencyReview(
        String invariantId,
        InvariantConsistencyDecision decision,
        List<InvariantConsistencyFinding> findings,
        String suggestedAction
) {
}
