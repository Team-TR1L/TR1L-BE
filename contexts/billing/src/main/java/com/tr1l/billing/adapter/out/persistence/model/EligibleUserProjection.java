package com.tr1l.billing.adapter.out.persistence.model;

import java.time.LocalDate;

public record EligibleUserProjection(
        long userId,
        String name,
        LocalDate birthDate,
        String phoneNumber,
        String email,
        String planCode,
        String welfareCode
) {
}
