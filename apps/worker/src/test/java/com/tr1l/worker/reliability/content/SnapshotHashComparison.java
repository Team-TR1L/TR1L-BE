package com.tr1l.worker.reliability.content;

import java.time.Instant;
import java.util.List;

// S-001 기준 snapshot 해시와 현재 해시 비교 결과 (C5)
public record SnapshotHashComparison(
        String scenarioId,
        String referenceScenarioId,
        Instant comparedAt,
        long referenceCount,
        long currentCount,
        long hashMatch,
        long hashMismatch,
        long missingInCurrent,
        long extraInCurrent,
        long duplicateUsers,
        List<Long> sampleMismatchUserIds,
        List<Long> sampleMissingUserIds,
        List<Long> sampleExtraUserIds
) {
    public boolean matched() {
        return hashMismatch == 0 && missingInCurrent == 0 && extraInCurrent == 0 && duplicateUsers == 0;
    }

    public long violationCount() {
        return hashMismatch + missingInCurrent + extraInCurrent + duplicateUsers;
    }
}
