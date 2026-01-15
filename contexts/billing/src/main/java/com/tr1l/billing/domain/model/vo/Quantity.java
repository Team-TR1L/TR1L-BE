package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record Quantity(int value) {
    public Quantity {
        if (value < 1) {
            throw new BillingDomainException(BillingErrorCode.INVALID_QUANTITY);
        }
    }
}