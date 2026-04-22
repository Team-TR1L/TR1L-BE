package com.tr1l.worker.reliability.invariant;

import java.time.Instant;
import java.util.List;

// S-001 run 직후 상태 카운트 묶음
public record Job1StateSnapshot(
        String billingMonthDay,
        Instant capturedAt,
        long billingTargetsCount,
        long billingWorkCount,
        long targetCount,
        long processingCount,
        long calculatedCount,
        long failedCount,
        long staleProcessingCount,
        long duplicateBillingWorkCount,
        long mongoSnapshotCount,
        long duplicateMongoSnapshotCount,
        List<String> sampleDuplicateMongoWorkIds
) {
    // 시나리오 지표 이름 매핑
    public long metricValue(String metricName) {
        return switch (metricName) {
            case "billingTargetsCount" -> billingTargetsCount;
            case "billingWorkCount" -> billingWorkCount;
            case "targetCount" -> targetCount;
            case "processingCount" -> processingCount;
            case "calculatedCount" -> calculatedCount;
            case "failedCount" -> failedCount;
            case "staleProcessingCount" -> staleProcessingCount;
            case "duplicateBillingWorkCount" -> duplicateBillingWorkCount;
            case "mongoSnapshotCount" -> mongoSnapshotCount;
            case "duplicateMongoSnapshotCount" -> duplicateMongoSnapshotCount;
            default -> throw new IllegalArgumentException("Unsupported metric: " + metricName);
        };
    }
}
