package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record BillingId(Long value) {
    public BillingId {
        if (value == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_BILLING_ID);
        }
    }
}
