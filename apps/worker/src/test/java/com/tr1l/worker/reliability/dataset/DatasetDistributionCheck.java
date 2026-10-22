package com.tr1l.worker.reliability.dataset;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// dataset.yml 분포와 실제 행 수 비교
// 비율 항목은 targetEligible 을 곱한 건수로 바꿔서 본다
public final class DatasetDistributionCheck {

    public DatasetDistributionResult compare(Job1DatasetDefinition dataset, Map<String, Long> actual) {
        List<DatasetDistributionResult.Row> rows = new ArrayList<>();
        JsonNode distribution = dataset.distribution();

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

        return new DatasetDistributionResult(dataset.id(), Instant.now(), rows);
    }

    // Ratio 로 끝나는 항목은 비율, 나머지는 건수
    private long expectedCount(String field, JsonNode value, long targetEligible) {
        if (field.endsWith("Ratio")) {
            return Math.round(value.asDouble() * targetEligible);
        }
        return value.asLong();
    }
}
