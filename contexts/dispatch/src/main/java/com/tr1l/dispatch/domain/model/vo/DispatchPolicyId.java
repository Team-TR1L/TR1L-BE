package com.tr1l.dispatch.domain.model.vo;

import java.util.UUID;

public record DispatchPolicyId(
        Long value
) {
    public static DispatchPolicyId generatePolicyId() {
        // 추후 ID Generator 사용
        return new DispatchPolicyId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
    }
}
