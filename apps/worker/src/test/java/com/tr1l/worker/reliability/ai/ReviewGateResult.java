package com.tr1l.worker.reliability.ai;

import java.util.List;

// review gate 판정 결과
public record ReviewGateResult(
        String generatedInvariantId,
        String activeInvariantId,
        boolean passed,
        List<String> blockers,
        List<String> warnings
) {
}
