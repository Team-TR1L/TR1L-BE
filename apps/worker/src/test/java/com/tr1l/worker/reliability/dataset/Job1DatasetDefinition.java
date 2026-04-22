package com.tr1l.worker.reliability.dataset;

import com.fasterxml.jackson.databind.JsonNode;

// 데이터셋 메타 파일 형태
public record Job1DatasetDefinition(
        String id,
        String description,
        String cutoff,
        String billingMonthDay,
        String billingYearMonth,
        Scale scale,
        JsonNode distribution,
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
