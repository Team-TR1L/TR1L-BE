package com.tr1l.billing.domain.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

import java.math.BigDecimal;

public record Rate(BigDecimal value) {
    public Rate {
        if (value == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_RATE);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BillingDomainException(BillingErrorCode.INVALID_RATE);
        }
    }

    /** 소수점 변환 */
    public static Rate ofPercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new BillingDomainException(BillingErrorCode.INVALID_RATE);
        }
        return new Rate(new BigDecimal(percent).movePointLeft(2)); // 25 -> 0.25
    }
}