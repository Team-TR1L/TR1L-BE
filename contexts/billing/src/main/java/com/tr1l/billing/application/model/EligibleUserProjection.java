package com.tr1l.billing.application.model;

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
