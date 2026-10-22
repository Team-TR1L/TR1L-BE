package com.tr1l.worker.reliability.dataset;

import java.util.List;
import java.util.Map;

// Job1 입력 차원 정의
// 차원 이름 순서를 유지하려고 LinkedHashMap 으로 읽힌다
public record InputDimensions(
        Map<String, Dimension> dimensions
) {
    // 차원 하나와 나올 수 있는 값
    public record Dimension(
            String description,
            List<String> values
    ) {
    }
}
