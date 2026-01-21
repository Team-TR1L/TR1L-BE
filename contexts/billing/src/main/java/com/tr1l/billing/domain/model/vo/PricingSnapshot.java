package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record PricingSnapshot(Money lineAmount) {

    public PricingSnapshot {
        // null값 방지
        if (lineAmount == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_PRICING_SNAPSHOT);
        }
    }


    /** lineAmount 계산 */
        public static PricingSnapshot of(Money lineAmount) {
            return new PricingSnapshot(lineAmount);
        }
}
