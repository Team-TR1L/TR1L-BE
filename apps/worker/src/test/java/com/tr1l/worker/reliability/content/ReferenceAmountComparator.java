package com.tr1l.worker.reliability.content;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 사용자, 항목 단위 금액 비교기
public final class ReferenceAmountComparator {
    // 결과 파일에 남길 표본 수
    private static final int SAMPLE_LIMIT = 20;

    public ReferenceAmountComparison compare(
            String scenarioId,
            Map<Long, BillingAmounts> expected,
            Map<Long, BillingAmounts> actual
    ) {
        long matchedUsers = 0;
        long mismatchedUsers = 0;
        long missing = 0;
        Map<String, Long> mismatchByField = new LinkedHashMap<>();
        List<ReferenceAmountComparison.Difference> samples = new ArrayList<>();

        for (Map.Entry<Long, BillingAmounts> entry : expected.entrySet()) {
            BillingAmounts snapshot = actual.get(entry.getKey());
            if (snapshot == null) {
                missing++;
                continue;
            }

            boolean userMatched = true;
            Map<String, Long> snapshotFields = snapshot.fields();
            for (Map.Entry<String, Long> field : entry.getValue().fields().entrySet()) {
                long actualValue = snapshotFields.get(field.getKey());
                if (actualValue != field.getValue()) {
                    userMatched = false;
                    mismatchByField.merge(field.getKey(), 1L, Long::sum);
                    if (samples.size() < SAMPLE_LIMIT) {
                        samples.add(new ReferenceAmountComparison.Difference(
                                entry.getKey(), field.getKey(), field.getValue(), actualValue));
                    }
                }
            }

            if (userMatched) {
                matchedUsers++;
            } else {
                mismatchedUsers++;
            }
        }

        long unexpected = actual.keySet().stream().filter(userId -> !expected.containsKey(userId)).count();

        return new ReferenceAmountComparison(
                scenarioId,
                Instant.now(),
                expected.size(),
                actual.size(),
                matchedUsers,
                mismatchedUsers,
                missing,
                unexpected,
                mismatchByField,
                samples
        );
    }
}
