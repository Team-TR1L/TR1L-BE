package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

import java.util.Optional;

public record Recipient(
        Optional<EncryptedEmail> encryptedEmail,
        Optional<EncryptedPhone> encryptedPhone
) {
    public Recipient {
        if (encryptedEmail == null || encryptedPhone == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_RECIPIENT);
        }
    }

    public boolean hasAnyContact() {
        return encryptedEmail.isPresent() || encryptedPhone.isPresent();
    }
}