package com.tr1l.worker.batch.calculatejob.model;

import java.time.Instant;

/**
 *
 ==========================
 *$class$
 *
 * @parm $parm$
 * @return $type
 * @author $nonstop
 * @version 1.0.0
 * @date $date
 * ========================== */
public record Step2WorkDoc(
        String id,      //_id = billingMonth:userId
        String billingMonth,
        long userId,
        String status,  //target
        int attemptCount,
        Instant createdAt,
        Instant updatedAt
) {
}
