package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

import java.time.YearMonth;

public record BillingPeriod(YearMonth value) {
    public BillingPeriod {
        if (value == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_BILLING_PERIOD);
        }
    }

    public String yyyyMm() {
        return value.toString(); // "2026-01" 타입 변환
    }
}