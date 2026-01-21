package com.tr1l.worker.batch.calculatejob.model;

import java.time.LocalDate;

/**
 step2 리더단계에서 필요한 targetKey
 * ========================== */
public record BillingTargetKey(LocalDate billingMonthDay, long userId) {

}
