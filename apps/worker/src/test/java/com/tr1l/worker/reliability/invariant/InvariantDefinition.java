package com.tr1l.worker.reliability.invariant;

// 활성 불변 조건 리소스 형태
public record InvariantDefinition(
        String id,
        String title,
        String category,
        String scope,
        String description,
        String checkType,
        String checkRef,
        int expectedViolationCount,
        String severity,
        Boolean allowedTransientViolation,
        String allureFeature,
        String allureStory
) {
    public InvariantCheckType type() {
        return InvariantCheckType.from(checkType);
    }
}
