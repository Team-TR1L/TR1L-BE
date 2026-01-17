package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record CustomerId(Long value) {
    public CustomerId {
        if (value == null || value <= 0) {
            throw new BillingDomainException(BillingErrorCode.INVALID_CUSTOMER_ID);
        }
    }

}