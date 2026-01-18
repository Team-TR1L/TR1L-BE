package com.tr1l.billing.application.port.out;

import java.time.Instant;
import java.time.LocalDate;


/**
 ==========================
 몽고에 저장하기 위한 레코드
 * ========================== */
public record WorkDocUpsertCommand(
        String id,
        String billingMonth,
        long userId,
        String status,
        int attemptCount,
        Instant createdAt) {

}
