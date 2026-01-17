package com.tr1l.dispatch.domain.model.vo;

import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.application.exception.DispatchDomainException;

import java.util.UUID;

public record DispatchPolicyId(
        Long value
) {

    public static DispatchPolicyId of(Long value) {
        if (value == null) {
            throw new DispatchDomainException(DispatchErrorCode.DISPATCH_POLICY_ID_NULL);
        }
        return new DispatchPolicyId(value);
    }

    public static DispatchPolicyId generatePolicyId() {
        // 추후 ID Generator 대체 가능
        long randomId = UUID.randomUUID()
                .getMostSignificantBits() & Long.MAX_VALUE;

        return DispatchPolicyId.of(randomId);
    }
}
