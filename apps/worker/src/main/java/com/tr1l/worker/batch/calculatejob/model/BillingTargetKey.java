package com.tr1l.worker.batch.calculatejob.model;

/**
 step2 리더단계에서 필요한 targetKey
 * ========================== */
public record BillingTargetKey(String billingMonth, long userId) {

}
