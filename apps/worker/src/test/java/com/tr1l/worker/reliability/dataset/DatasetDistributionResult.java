package com.tr1l.worker.reliability.dataset;

import java.time.Instant;
import java.util.List;

// 데이터셋 분포 비교 결과
public record DatasetDistributionResult(
        String datasetId,
        Instant checkedAt,
        List<Row> rows
) {
    public boolean matched() {
        return rows.stream().allMatch(Row::matched);
    }

    public List<Row> mismatchedRows() {
        return rows.stream().filter(row -> !row.matched()).toList();
    }

    // 분포 항목 하나
    public record Row(
            String metric,
            long expected,
            long actual,
            boolean matched
    ) {
    }
}
