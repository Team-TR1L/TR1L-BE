package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record EncryptedPhone(String cipherText) {
    public EncryptedPhone {
        if (cipherText == null || cipherText.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_ENCRYPTED_PHONE);
        }
    }
}