package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record Money(long amount) {
    public Money {
        if (amount <= 0) {
            throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        }
    }


    public Money plus(Money o) {
        if (o == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        return new Money(this.amount + o.amount);
    }

    /** 음수 방지 */
    public Money minusNonNegative(Money o) {
        if (o == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        long v = this.amount - o.amount;
        return new Money(Math.max(0, v));
    }

    /** 할인 상한이 있을 경우 사용 */
    public Money min(Money o) {
        if (o == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        return new Money(Math.min(this.amount, o.amount));
    }
}
