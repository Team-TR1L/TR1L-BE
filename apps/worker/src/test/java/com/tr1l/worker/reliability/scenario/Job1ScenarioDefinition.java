package com.tr1l.worker.reliability.scenario;

import java.util.List;

// 시나리오 리소스 형태
public record Job1ScenarioDefinition(
        String id,
        String name,
        String description,
        String datasetId,
        Job job,
        Fault fault,
        Rerun rerun,
        Validate validate,
        CompareWithBaseline compareWithBaseline,
        AllureLabels allure
) {
    public record Job(
            String name,
            String cutoff,
            String billingMonthDay
    ) {
    }

    public record Fault(
            boolean enabled
    ) {
    }

    public record Rerun(
            boolean enabled,
            Boolean sameCutoff
    ) {
    }

    public record Validate(
            String phase,
            List<String> invariants
    ) {
    }

    public record CompareWithBaseline(
            boolean enabled,
            String baselineScenarioId,
            List<String> metrics
    ) {
    }

    public record AllureLabels(
            String epic,
            String feature,
            String story
    ) {
    }

    // build 아티팩트 이름 정규화
    public String artifactDirectoryName() {
        return (id + "-" + name).replaceAll("[^a-zA-Z0-9-_]+", "-");
    }
}
