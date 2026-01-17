package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(long amount) {
    public Money {
        if (amount < 0) {
            throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        }
    }

    public static Money zero() {
        return new Money(0);
    }

    public boolean isZero() { return amount == 0; }


    public Money plus(Money o) {
        if (o == null) throw new BillingDomainException(BillingErrorCode.INVALID_MONEY);
        return new Money(this.amount + o.amount);
    }

    /** 음수 방지하는 뺄셈 */
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

    /** percent 할인 계산: Money * Rate  */
    public Money multiply(Rate rate) {
        if (rate == null) throw new BillingDomainException(BillingErrorCode.INVALID_RATE);

        // amount(원) * rate(0~1) -> 원 단위로 내림
        BigDecimal a = BigDecimal.valueOf(this.amount);
        BigDecimal r = rate.value();
        long discounted = a.multiply(r)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();

        return new Money(discounted);
    }
}
