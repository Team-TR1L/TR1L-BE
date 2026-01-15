package com.tr1l.billing.domain.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record SourceRef(String sourceType, Long sourceId) {
    public SourceRef {
        if (sourceType == null || sourceType.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_SOURCE_REF);
        }
        if (sourceId == null || sourceId <= 0) {
            throw new BillingDomainException(BillingErrorCode.INVALID_SOURCE_REF);
        }
    }
}