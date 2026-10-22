package com.tr1l.worker.reliability.dataset;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

// 데이터셋 메타 파일 형태
// distribution 은 정확한 건수 비교 (D1)
// dimensionRatios 는 허용 오차 안의 비율 비교 (생성 데이터셋)
// boundaryCases 는 경계값 경우별 기대 결과 (D2)
public record Job1DatasetDefinition(
        String id,
        String description,
        String cutoff,
        String billingMonthDay,
        String billingYearMonth,
        JsonNode generator,
        Scale scale,
        JsonNode distribution,
        Map<String, Map<String, Double>> dimensionRatios,
        Double ratioTolerance,
        Double minPairwiseCoverage,
        JsonNode boundaryCases,
        JsonNode expected
) {
    // 규모 정보 전용 영역
    public record Scale(
            long usersTotal,
            long targetEligible,
            long excluded
    ) {
    }
}
