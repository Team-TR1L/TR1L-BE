package com.tr1l.dispatch.domain.model.vo;

import com.tr1l.dispatch.application.exception.DispatchErrorCode;
import com.tr1l.dispatch.application.exception.DispatchDomainException;

public record AdminId(
        Long value
) {
    public static AdminId of(Long value) {
        if (value == null)
            throw new DispatchDomainException(DispatchErrorCode.ADMIN_ID_NULL);
        return new AdminId(value);
    }
}
