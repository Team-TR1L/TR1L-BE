package com.tr1l.worker.reliability.coverage;

import com.tr1l.worker.reliability.invariant.InvariantDefinition;
import com.tr1l.worker.reliability.scenario.Job1ScenarioDefinition;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 시나리오 리소스와 active invariant 연결 계산
public final class InvariantCoverageMatrixGenerator {
    public InvariantCoverageMatrix generate(
            List<Job1ScenarioDefinition> scenarios,
            List<InvariantDefinition> activeInvariants
    ) {
        List<Job1ScenarioDefinition> sortedScenarios = scenarios.stream()
                .sorted(Comparator.comparing(Job1ScenarioDefinition::id))
                .toList();

        List<InvariantDefinition> sortedInvariants = activeInvariants.stream()
                .sorted(Comparator.comparing(InvariantDefinition::id))
                .toList();

        Set<String> activeInvariantIds = sortedInvariants.stream()
                .map(InvariantDefinition::id)
                .collect(Collectors.toSet());

        List<String> scenarioIds = sortedScenarios.stream()
                .map(Job1ScenarioDefinition::id)
                .toList();

        List<InvariantCoverageRow> rows = sortedInvariants.stream()
                .map(invariant -> toRow(invariant, sortedScenarios))
                .toList();

        List<String> uncoveredInvariantIds = rows.stream()
                .filter(row -> row.coverageCount() == 0)
                .map(InvariantCoverageRow::invariantId)
                .toList();

        List<String> scenariosWithoutInvariants = sortedScenarios.stream()
                .filter(scenario -> invariantIdsOf(scenario).isEmpty())
                .map(Job1ScenarioDefinition::id)
                .toList();

        List<InvariantCoverageMatrix.UnknownInvariantReference> unknownReferences = sortedScenarios.stream()
                .flatMap(scenario -> invariantIdsOf(scenario).stream()
                        .filter(invariantId -> !activeInvariantIds.contains(invariantId))
                        .map(invariantId -> new InvariantCoverageMatrix.UnknownInvariantReference(
                                scenario.id(),
                                invariantId
                        )))
                .toList();

        return new InvariantCoverageMatrix(
                scenarioIds,
                sortedScenarios.size(),
                sortedInvariants.size(),
                rows,
                uncoveredInvariantIds,
                scenariosWithoutInvariants,
                unknownReferences
        );
    }

    // 불변 조건 하나의 coverage row 생성
    private InvariantCoverageRow toRow(
            InvariantDefinition invariant,
            List<Job1ScenarioDefinition> scenarios
    ) {
        List<String> coveredScenarioIds = scenarios.stream()
                .filter(scenario -> invariantIdsOf(scenario).contains(invariant.id()))
                .map(Job1ScenarioDefinition::id)
                .toList();

        return new InvariantCoverageRow(
                invariant.id(),
                invariant.title(),
                invariant.category(),
                invariant.scope(),
                coveredScenarioIds,
                coveredScenarioIds.size()
        );
    }

    // 시나리오에 연결된 invariant id 목록 추출
    private Set<String> invariantIdsOf(Job1ScenarioDefinition scenario) {
        if (scenario.validate() == null || scenario.validate().invariants() == null) {
            return Set.of();
        }
        return new HashSet<>(scenario.validate().invariants());
    }
}
