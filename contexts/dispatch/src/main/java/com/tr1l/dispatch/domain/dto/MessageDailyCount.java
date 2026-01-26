package com.tr1l.dispatch.domain.dto;

import java.time.LocalDate;

public record MessageDailyCount(
        LocalDate date,
        Long count
) {}
