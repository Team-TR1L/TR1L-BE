package com.tr1l.worker.reliability.invariant;

import com.tr1l.worker.reliability.scenario.Job1ScenarioDefinition;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// 시나리오 선택 불변 조건 실행기
// 단순 실행 흐름 유지
public final class InvariantValidator {
    private final PostgresInvariantChecks postgresChecks;
    private final CrossDbInvariantChecks crossDbChecks;

    public InvariantValidator(
            PostgresInvariantChecks postgresChecks,
            CrossDbInvariantChecks crossDbChecks
    ) {
        this.postgresChecks = postgresChecks;
        this.crossDbChecks = crossDbChecks;
    }

    public InvariantValidationResult validate(
            Job1ScenarioDefinition scenario,
            List<InvariantDefinition> activeInvariants
    ) {
        // 시나리오 기준 billingMonthDay
        // 파라미터 계산 로직 배제
        LocalDate billingMonthDay = LocalDate.parse(scenario.job().billingMonthDay());
        String phase = scenario.validate().phase();
        Set<String> selectedIds = Set.copyOf(scenario.validate().invariants());

        // 범위 불일치 불변 조건 제외
        List<InvariantResult> results = activeInvariants.stream()
                .filter(definition -> selectedIds.contains(definition.id()))
                .filter(definition -> scopeApplies(definition.scope(), phase))
                .map(definition -> switch (definition.type()) {
                    case POSTGRES -> postgresChecks.execute(definition, billingMonthDay);
                    case CROSS_DB -> crossDbChecks.execute(definition, billingMonthDay);
                })
                .toList();

        return new InvariantValidationResult(
                scenario.id(),
                billingMonthDay.toString(),
                phase,
                Instant.now(),
                results
        );
    }

    private boolean scopeApplies(String scope, String phase) {
        // S-001R 에서도 같은 범위 재사용
        // 최종 상태 기준 통일
        if ("always".equalsIgnoreCase(scope)) {
            return true;
        }
        if (scope.equalsIgnoreCase(phase)) {
            return true;
        }
        return "after_rerun_complete".equalsIgnoreCase(scope)
                && "after_job_complete".equalsIgnoreCase(phase);
    }
}
