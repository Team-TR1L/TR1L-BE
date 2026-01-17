package com.tr1l.worker.batch.calculatejob.model;

import java.time.Instant;

/**
 * step1 에서 읽어온 뷰를 몽고에 넣기위한 레코드
 * ========================== */
public record WorkDoc(
        String id,      //_id = billingMonth:userId
        String billingMonth,
        long userId,
        String status,  //target
        int attemptCount,
        Instant createdAt,
        Instant updatedAt
) {
}
