package com.tr1l.worker.reliability.invariant;

import java.util.List;
import java.util.Map;

// 불변 조건 단건 결과 모델
public record InvariantResult(
        String invariantId,
        String title,
        String scope,
        String checkType,
        boolean passed,
        int violationCount,
        List<Map<String, Object>> sampleViolations
) {
}
