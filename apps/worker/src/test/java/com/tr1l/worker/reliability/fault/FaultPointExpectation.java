package com.tr1l.worker.reliability.fault;

import com.tr1l.worker.reliability.invariant.Job1StateSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 장애 직후 예상 상태 비교
// 조건은 "== 0", "< total", "== total" 처럼 연산자와 값으로 적는다
public final class FaultPointExpectation {

    public Result evaluate(String point, Map<String, String> expect, Job1StateSnapshot snapshot, long total) {
        List<Result.Check> checks = new ArrayList<>();
        expect.forEach((metric, condition) -> {
            long actual = snapshot.metricValue(metric);
            checks.add(new Result.Check(metric, condition, actual, matches(condition, actual, total)));
        });
        return new Result(point, Instant.now(), total, checks);
    }

    private boolean matches(String condition, long actual, long total) {
        String[] parts = condition.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Unsupported condition: " + condition);
        }
        long expected = "total".equals(parts[1]) ? total : Long.parseLong(parts[1]);
        return switch (parts[0]) {
            case "==" -> actual == expected;
            case "<" -> actual < expected;
            case "<=" -> actual <= expected;
            case ">" -> actual > expected;
            case ">=" -> actual >= expected;
            default -> throw new IllegalArgumentException("Unsupported operator: " + condition);
        };
    }

    // 장애 지점 하나의 비교 결과
    public record Result(
            String point,
            Instant checkedAt,
            long total,
            List<Check> checks
    ) {
        public boolean matched() {
            return checks.stream().allMatch(Check::matched);
        }

        // 지표 하나
        public record Check(
                String metric,
                String condition,
                long actual,
                boolean matched
        ) {
        }
    }
}
