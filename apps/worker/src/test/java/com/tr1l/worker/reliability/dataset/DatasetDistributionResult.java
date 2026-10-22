package com.tr1l.worker.reliability.dataset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// 데이터셋 분포 비교 결과
public record DatasetDistributionResult(
        String datasetId,
        Instant checkedAt,
        List<Row> rows,
        List<RatioRow> ratioRows,
        DimensionProfile dimensions,
        Double minPairwiseCoverage
) {
    public boolean matched() {
        return problems().isEmpty();
    }

    public List<Row> mismatchedRows() {
        return rows.stream().filter(row -> !row.matched()).toList();
    }

    // 실패 사유 모음
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        mismatchedRows().forEach(row -> problems.add(row.metric() + " expected " + row.expected() + " actual " + row.actual()));
        ratioRows.stream()
                .filter(row -> !row.matched())
                .forEach(row -> problems.add(row.metric() + " expected " + row.expected() + " actual " + row.actual()));
        if (dimensions != null) {
            dimensions.unknownValues().forEach(value -> problems.add("unknown dimension value " + value));
            if (minPairwiseCoverage != null && dimensions.pairwiseCoverage() < minPairwiseCoverage) {
                problems.add("pairwise coverage " + dimensions.pairwiseCoverage() + " < " + minPairwiseCoverage);
            }
        }
        return problems;
    }

    // 정확한 건수 항목 하나
    public record Row(
            String metric,
            long expected,
            long actual,
            boolean matched
    ) {
    }

    // 비율 항목 하나
    public record RatioRow(
            String metric,
            double expected,
            double actual,
            boolean matched
    ) {
    }
}
