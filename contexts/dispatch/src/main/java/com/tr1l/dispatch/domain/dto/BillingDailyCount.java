package com.tr1l.dispatch.domain.dto;

import java.time.LocalDate;

public record BillingDailyCount(
        LocalDate billingMonth,
        String dayTime,
        Long count
) {}
