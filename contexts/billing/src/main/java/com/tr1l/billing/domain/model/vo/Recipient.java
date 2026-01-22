package com.tr1l.billing.domain.model.vo;

import com.tr1l.billing.domain.exception.BillingDomainException;
import com.tr1l.billing.error.BillingErrorCode;

import java.util.Optional;

public record Recipient(
        Optional<EncryptedEmail> email,
        Optional<EncryptedPhone> phone
) {
    public Recipient {
        if (email == null || phone == null) {
            throw new BillingDomainException(BillingErrorCode.INVALID_RECIPIENT);
        }
    }

    public boolean hasAnyContact() {
        return email.isPresent() || phone.isPresent();
    }
}