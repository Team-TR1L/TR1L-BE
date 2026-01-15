package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

public record EncryptedEmail(String cipherText) {
    public EncryptedEmail {
        if (cipherText == null || cipherText.isBlank()) {
            throw new BillingDomainException(BillingErrorCode.INVALID_ENCRYPTED_EMAIL);
        }
    }
}