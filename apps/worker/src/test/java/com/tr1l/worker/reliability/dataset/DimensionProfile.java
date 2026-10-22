package com.tr1l.worker.reliability.dataset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 청구 대상 사용자의 차원 값 분포와 pairwise 커버리지
public record DimensionProfile(
        long targetUsers,
        Map<String, Map<String, Long>> valueCounts,
        long possiblePairs,
        long coveredPairs,
        double pairwiseCoverage,
        List<String> missingPairs,
        List<String> unknownValues
) {
    // 결과 파일에 남길 빠진 쌍 수
    private static final int MISSING_SAMPLE_LIMIT = 50;

    public static DimensionProfile of(InputDimensions definition, List<Map<String, String>> users) {
        List<String> names = new ArrayList<>(definition.dimensions().keySet());

        // 차원별 값 개수
        Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
        for (String name : names) {
            Map<String, Long> values = new LinkedHashMap<>();
            definition.dimensions().get(name).values().forEach(value -> values.put(value, 0L));
            counts.put(name, values);
        }

        // 실제로 나온 값 쌍
        Set<String> observedPairs = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (Map<String, String> user : users) {
            for (String name : names) {
                String value = user.get(name);
                if (counts.get(name).containsKey(value)) {
                    counts.get(name).merge(value, 1L, Long::sum);
                } else {
                    unknown.add(name + "=" + value);
                }
            }
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    observedPairs.add(pairKey(names.get(i), user.get(names.get(i)), names.get(j), user.get(names.get(j))));
                }
            }
        }

        // 나올 수 있는 값 쌍 전체와 비교
        long possible = 0;
        long covered = 0;
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                for (String left : definition.dimensions().get(names.get(i)).values()) {
                    for (String right : definition.dimensions().get(names.get(j)).values()) {
                        possible++;
                        String key = pairKey(names.get(i), left, names.get(j), right);
                        if (observedPairs.contains(key)) {
                            covered++;
                        } else if (missing.size() < MISSING_SAMPLE_LIMIT) {
                            missing.add(key);
                        }
                    }
                }
            }
        }

        return new DimensionProfile(
                users.size(),
                counts,
                possible,
                covered,
                possible == 0 ? 0 : (double) covered / possible,
                missing,
                new ArrayList<>(unknown)
        );
    }

    public double ratio(String dimension, String value) {
        Long count = valueCounts.getOrDefault(dimension, Map.of()).get(value);
        return count == null || targetUsers == 0 ? 0 : (double) count / targetUsers;
    }

    private static String pairKey(String leftName, String leftValue, String rightName, String rightValue) {
        return leftName + "=" + leftValue + " × " + rightName + "=" + rightValue;
    }
}
