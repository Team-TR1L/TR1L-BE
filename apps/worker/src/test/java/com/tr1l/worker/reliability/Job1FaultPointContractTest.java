package com.tr1l.worker.reliability;

import com.tr1l.worker.batch.fault.Job1FaultPoint;
import com.tr1l.worker.reliability.fault.FaultPointExpectation;
import com.tr1l.worker.reliability.fault.FaultPoints;
import com.tr1l.worker.reliability.invariant.Job1StateSnapshot;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 장애 주입 지점 리소스와 예상 상태 규칙 고정
// DB 없이 리소스와 계산만 본다
class Job1FaultPointContractTest {
    private static final Set<String> CHUNK_KEYS = Set.of("none", "step1", "step2", "step3-claim", "step3-write");

    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();

    @Test
    @DisplayName("리소스의 장애 지점이 운영 코드 Job1FaultPoint 와 같은지 보기")
    void faultPointsShouldMatchProductionEnum() {
        FaultPoints points = catalog.loadFaultPoints();

        assertThat(points.points().keySet())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(Job1FaultPoint.values()).map(Enum::name).toList());
        points.points().forEach((name, point) -> {
            assertThat(CHUNK_KEYS).as("%s chunkKey", name).contains(point.chunkKey());
            assertThat(point.expect()).as("%s expect", name).isNotEmpty();
        });
        points.points().forEach((name, point) -> {
            if (point.expectByKind() != null) {
                assertThat(point.expectByKind().keySet()).as("%s expectByKind", name).isSubsetOf("K1", "K2", "K3", "K4", "K5");
            }
        });
        // K4 는 Step0 ~ 2 에서 MongoDB 를 쓰지 않아 Step3 첫 저장에서 실패한다
        assertThat(points.points().get("P1").expectFor("K4")).containsEntry("billingTargetsCount", "== total");
        assertThat(points.points().get("P1").expectFor("K2")).isEqualTo(points.points().get("P1").expect());
        assertThat(points.points().get("P0").chunkKey()).isEqualTo("none");
        assertThat(points.points().get("P4").chunkKey()).isEqualTo("none");
    }

    @Test
    @DisplayName("예상 상태 조건이 total 과 숫자를 바르게 비교하는지 보기")
    void expectationShouldEvaluateConditions() {
        FaultPointExpectation expectation = new FaultPointExpectation();
        Job1StateSnapshot snapshot = snapshot(28_500, 14_000, 10_000);

        FaultPointExpectation.Result matched = expectation.evaluate("P2", Map.of(
                "billingTargetsCount", "== total",
                "billingWorkCount", "< total",
                "mongoSnapshotCount", "> 0"
        ), snapshot, 28_500);
        FaultPointExpectation.Result unmatched = expectation.evaluate("P1", Map.of(
                "billingWorkCount", "== 0"
        ), snapshot, 28_500);

        assertThat(matched.matched()).isTrue();
        assertThat(unmatched.matched()).isFalse();
        assertThat(unmatched.checks()).extracting(FaultPointExpectation.Result.Check::actual).containsExactly(14_000L);
    }

    @Test
    @DisplayName("장애 행렬 시나리오가 장애 직후와 재실행 두 단계로 나뉘는지 보기")
    void faultMatrixScenariosShouldSplitFaultAndRerun() {
        var fault = catalog.loadScenario("S-010");
        var rerun = catalog.loadScenario("S-010R");

        assertThat(fault.fault().enabled()).isTrue();
        assertThat(fault.validate().phase()).isEqualTo("after_fault");
        assertThat(rerun.rerun().enabled()).isTrue();
        assertThat(rerun.compareWithBaseline().baselineScenarioId()).isEqualTo("S-001");
        assertThat(rerun.validate().invariants()).contains("INV-001", "INV-002", "INV-003", "INV-004");
    }

    private Job1StateSnapshot snapshot(long targets, long works, long snapshots) {
        return new Job1StateSnapshot("2026-01-01", Instant.now(), targets, works, works, 0, works, 0, 0, 0, 0, 0,
                snapshots, 0, 0, 0, List.of(), List.of(), List.of());
    }
}
