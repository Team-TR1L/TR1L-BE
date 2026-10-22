package com.tr1l.worker.reliability.dataset;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// dataset.yml 분포와 실제 데이터 비교
// distribution 의 비율 항목은 targetEligible 을 곱한 건수로 바꿔서 본다
// dimensionRatios 는 청구 대상 사용자 비율을 ratioTolerance 안에서 본다
public final class DatasetDistributionCheck {

    public DatasetDistributionResult compare(Job1DatasetDefinition dataset, Map<String, Long> actual) {
        return compare(dataset, actual, null);
    }

    public DatasetDistributionResult compare(
            Job1DatasetDefinition dataset,
            Map<String, Long> actual,
            DimensionProfile profile
    ) {
        return new DatasetDistributionResult(
                dataset.id(),
                Instant.now(),
                exactRows(dataset, actual),
                profile == null ? List.of() : ratioRows(dataset, profile),
                profile,
                dataset.minPairwiseCoverage()
        );
    }

    private List<DatasetDistributionResult.Row> exactRows(Job1DatasetDefinition dataset, Map<String, Long> actual) {
        List<DatasetDistributionResult.Row> rows = new ArrayList<>();
        JsonNode distribution = dataset.distribution();
        if (distribution == null) {
            return rows;
        }

        for (Map.Entry<String, JsonNode> group : distribution.properties()) {
            for (Map.Entry<String, JsonNode> field : group.getValue().properties()) {
                String metric = group.getKey() + "." + field.getKey();
                long expected = expectedCount(field.getKey(), field.getValue(), dataset.scale().targetEligible());
                Long actualValue = actual.get(metric);
                rows.add(new DatasetDistributionResult.Row(
                        metric,
                        expected,
                        actualValue == null ? 0 : actualValue,
                        actualValue != null && actualValue == expected
                ));
            }
        }
        return rows;
    }

    private List<DatasetDistributionResult.RatioRow> ratioRows(Job1DatasetDefinition dataset, DimensionProfile profile) {
        List<DatasetDistributionResult.RatioRow> rows = new ArrayList<>();
        if (dataset.dimensionRatios() == null) {
            return rows;
        }

        double tolerance = dataset.ratioTolerance() == null ? 0 : dataset.ratioTolerance();
        dataset.dimensionRatios().forEach((dimension, values) -> values.forEach((value, expected) -> {
            double actual = profile.ratio(dimension, value);
            rows.add(new DatasetDistributionResult.RatioRow(
                    dimension + "." + value,
                    expected,
                    actual,
                    Math.abs(actual - expected) <= tolerance
            ));
        }));
        return rows;
    }

    // Ratio 로 끝나는 항목은 비율, 나머지는 건수
    private long expectedCount(String field, JsonNode value, long targetEligible) {
        if (field.endsWith("Ratio")) {
            return Math.round(value.asDouble() * targetEligible);
        }
        return value.asLong();
    }
}
