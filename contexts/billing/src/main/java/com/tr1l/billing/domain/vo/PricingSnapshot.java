package com.tr1l.billing.domain.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record PricingSnapshot(Quantity quantity, Money unitPrice, Money lineAmount) {

    public PricingSnapshot {
        // null값 방지
        if (quantity == null || unitPrice == null || lineAmount == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_PRICING_SNAPSHOT);
        }

        final long expected = calculateLineAmount(quantity, unitPrice);

        if (expected != lineAmount.amount()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_PRICING_SNAPSHOT);
        }
    }

    /** overflow 방어 */
    public static long calculateLineAmount(Quantity q, Money unitPrice) {
        if (q == null || unitPrice == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_PRICING_SNAPSHOT);
        }
        try {
            return Math.multiplyExact(unitPrice.amount(), (long) q.value());
        } catch (ArithmeticException e) { // overflow
            throw new BillingDomainException(BillingErrorCode.INVALID_PRICING_SNAPSHOT, e);
        }
    }

    /** lineAmount 계산 */
    public static PricingSnapshot of(Quantity q, Money unitPrice) {
        long line = calculateLineAmount(q, unitPrice);
        return new PricingSnapshot(q, unitPrice, new Money(line));
    }
}
