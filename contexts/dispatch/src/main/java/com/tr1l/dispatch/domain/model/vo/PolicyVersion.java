package com.tr1l.dispatch.domain.model.vo;

import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.application.exception.DispatchDomainException;

public record PolicyVersion(
        Integer value
) {
    public static PolicyVersion of(Integer value) {
        if (value == null)
            throw new DispatchDomainException(DispatchErrorCode.POLICY_VERSION_NULL);
        if (value < 1)
            throw new DispatchDomainException(DispatchErrorCode.POLICY_VERSION_INVALID);
        return new PolicyVersion(value);
    }

    public PolicyVersion next() {
        if (value == Integer.MAX_VALUE) {
            throw new DispatchDomainException(DispatchErrorCode.POLICY_VERSION_OVERFLOW);
        }
        return PolicyVersion.of(this.value + 1);
    }
}

