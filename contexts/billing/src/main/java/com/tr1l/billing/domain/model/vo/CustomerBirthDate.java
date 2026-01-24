package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

import java.time.LocalDate;


public record CustomerBirthDate(LocalDate value) {
    public CustomerBirthDate {
        if(value == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_USER_BIRTH_DATE);
        }
    }
}
