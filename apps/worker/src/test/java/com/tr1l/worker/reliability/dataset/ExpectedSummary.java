package com.tr1l.worker.reliability.dataset;

// 기대값 범위 정의
public record ExpectedSummary(
        String datasetId,
        String billingMonthDay,
        String billingYearMonth,
        Expected expected
) {
    // 정확한 수치 고정 전 범위값
    public record Expected(
            long usersTotal,
            long targetEligible,
            Range billingTargets,
            Range billingWork,
            Range mongoSnapshotsAfterNormalRun,
            long failedAfterNormalRun,
            long staleProcessingAfterRerun,
            long duplicateBillingWork,
            long duplicateMongoWorkId
    ) {
    }

    // 범위 검증 전용 타입
    public record Range(
            long min,
            long max
    ) {
        public boolean contains(long value) {
            return value >= min && value <= max;
        }
    }
}
