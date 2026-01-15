package com.tr1l.billing.domain.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
    }
}