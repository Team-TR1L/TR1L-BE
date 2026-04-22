package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.dataset.ExpectedSummary;
import com.tr1l.worker.reliability.dataset.Job1DatasetDefinition;
import com.tr1l.worker.reliability.invariant.InvariantDefinition;
import com.tr1l.worker.reliability.scenario.Job1ScenarioDefinition;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 상시 실행 대상
// 리소스 구조 선검증 용도
class Job1ReliabilityResourceContractTest {
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();

    @Test
    @DisplayName("D1 데이터셋 값이 지금 기준이랑 맞는지 보기")
    void d1DatasetContract_should_be_consistent() {
        // D1 데이터셋 계약 검증
        Job1DatasetDefinition dataset = catalog.loadDataset("D1-rerun-mvp");
        ExpectedSummary expectedSummary = catalog.loadExpectedSummary("D1-rerun-mvp");

        assertThat(dataset.id()).isEqualTo("D1-rerun-mvp");
        assertThat(dataset.billingMonthDay()).isEqualTo("2026-01-01");
        assertThat(dataset.billingYearMonth()).isEqualTo("2026-01");
        assertThat(dataset.scale().usersTotal()).isEqualTo(30_000);
        assertThat(dataset.scale().targetEligible()).isEqualTo(28_500);
        assertThat(expectedSummary.datasetId()).isEqualTo(dataset.id());
        assertThat(expectedSummary.expected().billingTargets().contains(28_500)).isTrue();
        assertThat(expectedSummary.expected().billingWork().contains(28_500)).isTrue();
        assertThat(expectedSummary.expected().mongoSnapshotsAfterNormalRun().contains(28_500)).isTrue();
    }

    @Test
    @DisplayName("active invariant 파일이 실제 체크 파일이랑 이어지는지 보기")
    void activeInvariants_should_reference_existing_checks() {
        // 불변 조건 리소스 연결 검증
        List<InvariantDefinition> invariants = catalog.loadActiveInvariants();

        assertThat(invariants).hasSize(3);
        assertThat(invariants).extracting(InvariantDefinition::id)
                .containsExactly("INV-001", "INV-002", "INV-003");
        assertThat(invariants).allSatisfy(definition -> {
            assertThat(catalog.resourceExists(definition.checkRef()))
                    .as("checkRef exists for %s", definition.id())
                    .isTrue();
            assertThat(definition.description()).isNotBlank();
            assertThat(definition.scope()).isNotBlank();
        });
    }

    @Test
    @DisplayName("S-001 시나리오가 D1 run 직후 상태 스냅샷 기준으로 보게 되어 있는지 보기")
    void baselineScenarios_should_target_d1_and_known_invariants() {
        // S-001 run 직후 상태 스냅샷 시나리오 구조
        // 후속 시나리오 기준점 용도
        Job1ScenarioDefinition normalRun = catalog.loadScenario("S-001");
        Job1ScenarioDefinition rerun = catalog.loadScenario("S-001R");

        assertThat(normalRun.datasetId()).isEqualTo("D1-rerun-mvp");
        assertThat(normalRun.validate().invariants()).containsExactly("INV-001", "INV-002", "INV-003");
        assertThat(rerun.datasetId()).isEqualTo("D1-rerun-mvp");
        assertThat(rerun.compareWithBaseline()).isNotNull();
        assertThat(rerun.compareWithBaseline().baselineScenarioId()).isEqualTo("S-001");
        assertThat(rerun.compareWithBaseline().metrics())
                .contains("billingTargetsCount", "billingWorkCount", "calculatedCount", "mongoSnapshotCount");
    }
}
