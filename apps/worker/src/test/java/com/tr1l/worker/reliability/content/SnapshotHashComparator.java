package com.tr1l.worker.reliability.content;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 사용자 단위 snapshot 해시 비교기
public final class SnapshotHashComparator {
    // 결과 파일에 남길 표본 수
    private static final int SAMPLE_LIMIT = 20;

    public SnapshotHashComparison compare(
            String scenarioId,
            String referenceScenarioId,
            SnapshotHashes reference,
            SnapshotHashes current
    ) {
        long match = 0;
        long mismatch = 0;
        long missing = 0;
        List<Long> sampleMismatch = new ArrayList<>();
        List<Long> sampleMissing = new ArrayList<>();
        List<Long> sampleExtra = new ArrayList<>();

        for (Map.Entry<Long, String> entry : reference.hashes().entrySet()) {
            String currentHash = current.hashes().get(entry.getKey());
            if (currentHash == null) {
                missing++;
                addSample(sampleMissing, entry.getKey());
            } else if (currentHash.equals(entry.getValue())) {
                match++;
            } else {
                mismatch++;
                addSample(sampleMismatch, entry.getKey());
            }
        }

        long extra = 0;
        for (Long userId : current.hashes().keySet()) {
            if (!reference.hashes().containsKey(userId)) {
                extra++;
                addSample(sampleExtra, userId);
            }
        }

        return new SnapshotHashComparison(
                scenarioId,
                referenceScenarioId,
                Instant.now(),
                reference.hashes().size(),
                current.hashes().size(),
                match,
                mismatch,
                missing,
                extra,
                current.duplicateUsers().size(),
                sampleMismatch,
                sampleMissing,
                sampleExtra
        );
    }

    private void addSample(List<Long> samples, Long userId) {
        if (samples.size() < SAMPLE_LIMIT) {
            samples.add(userId);
        }
    }
}
