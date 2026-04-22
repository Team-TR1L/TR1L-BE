package com.tr1l.worker.reliability.invariant;

import java.time.Instant;
import java.util.List;

// 시나리오 단위 결과 묶음
public record InvariantValidationResult(
        String scenarioId,
        String billingMonthDay,
        String validationPhase,
        Instant checkedAt,
        List<InvariantResult> results
) {
    public boolean allPassed() {
        return results.stream().allMatch(InvariantResult::passed);
    }
}
