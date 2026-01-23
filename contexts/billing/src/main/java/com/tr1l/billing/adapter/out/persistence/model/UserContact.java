package com.tr1l.billing.adapter.out.persistence.model;

public record UserContact(
        String email,
        String phoneNumber
) {
    public boolean hasEmail(){
        return email != null && !email.isBlank();
    }

    public boolean hasPhone(){
        return phoneNumber != null && !phoneNumber.isBlank();
    }
}
