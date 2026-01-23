package com.tr1l.billing.adapter.out.persistence.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * step1 에서 읽어온 뷰를 몽고에 넣기위한 레코드
 * ========================== */
public record WorkDoc(
        String id,      //_id = billingMonth:userId
        LocalDate billingMonthDay,
        long userId,
        String status,  //target
        int attemptCount,
        Instant createdAt,
        Instant updatedAt
) {
}
