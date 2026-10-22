package com.tr1l.worker.reliability.fault;

import java.util.Map;

// 장애 주입 지점 정의 (faults/job1-fault-points.yml)
public record FaultPoints(
        Map<String, Point> points
) {
    // 지점 하나
    public record Point(
            String step,
            String chunkKey,
            Map<String, String> expect,
            Map<String, Map<String, String>> expectByKind
    ) {
        // 장애 종류별 예상 상태. 따로 없으면 기본 expect
        public Map<String, String> expectFor(String kind) {
            if (kind != null && expectByKind != null && expectByKind.containsKey(kind)) {
                return expectByKind.get(kind);
            }
            return expect;
        }
    }
}
