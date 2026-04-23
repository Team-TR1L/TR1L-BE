package com.tr1l.worker.reliability.ai;

// consistency checker 단건 지적
public record InvariantConsistencyFinding(
        String code,
        String severity,
        String field,
        String message
) {
}
