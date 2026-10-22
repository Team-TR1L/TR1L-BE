package com.tr1l.worker.reliability.content;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// 참조 계산 금액과 snapshot 금액 비교 결과 (C6)
public record ReferenceAmountComparison(
        String scenarioId,
        Instant comparedAt,
        long expectedUsers,
        long snapshotUsers,
        long matchedUsers,
        long mismatchedUsers,
        long missingSnapshots,
        long unexpectedSnapshots,
        Map<String, Long> mismatchByField,
        List<Difference> sampleDifferences
) {
    public boolean matched() {
        return mismatchedUsers == 0 && missingSnapshots == 0 && unexpectedSnapshots == 0;
    }

    public long violationCount() {
        return mismatchedUsers + missingSnapshots + unexpectedSnapshots;
    }

    // 금액이 다른 항목 하나
    public record Difference(
            long userId,
            String field,
            long expected,
            long actual
    ) {
    }
}
