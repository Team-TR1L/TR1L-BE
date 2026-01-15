package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record DiscountCap(Money maxDiscountAmount) {
    public DiscountCap {
        if (maxDiscountAmount == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_DISCOUNT_CAP);
        }
    }
}
