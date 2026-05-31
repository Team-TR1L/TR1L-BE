package com.tr1l.worker.reliability.support;

import com.tr1l.worker.reliability.dataset.ExpectedSummary;
import com.tr1l.worker.reliability.dataset.Job1DatasetDefinition;
import com.tr1l.worker.reliability.invariant.InvariantDefinition;
import com.tr1l.worker.reliability.scenario.Job1ScenarioDefinition;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

// reliability 리소스 위치 집약
// 경로 문자열 분산 방지
public final class ReliabilityResourceCatalog {
    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public Job1DatasetDefinition loadDataset(String datasetId) {
        return loader.loadYaml("reliability/datasets/" + datasetId + "/dataset.yml", Job1DatasetDefinition.class);
    }

    public ExpectedSummary loadExpectedSummary(String datasetId) {
        return loader.loadJson("reliability/datasets/" + datasetId + "/expected-summary.json", ExpectedSummary.class);
    }

    public List<InvariantDefinition> loadActiveInvariants() {
        return readJsonDirectory("classpath*:reliability/invariants/active/*.json", InvariantDefinition.class);
    }

    public List<Job1ScenarioDefinition> loadScenarios() {
        return readYamlDirectory("classpath*:reliability/scenarios/*.yml", Job1ScenarioDefinition.class);
    }

    public Job1ScenarioDefinition loadScenario(String scenarioId) {
        return loadScenarios().stream()
                // 시나리오 id 기준 단건 조회
                .filter(definition -> scenarioId.equals(definition.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Scenario resource not found for id: " + scenarioId));
    }

    public String loadCheckText(String classpathLocation) {
        return loader.loadText(classpathLocation);
    }

    public boolean resourceExists(String classpathLocation) {
        return loader.exists(classpathLocation);
    }

    private <T> List<T> readJsonDirectory(String pattern, Class<T> type) {
        try {
            Resource[] resources = resolver.getResources(pattern);
            return Arrays.stream(resources)
                    // 고정 순서 정렬
                    .sorted(Comparator.comparing(resource -> {
                        try {
                            return resource.getFilename();
                        } catch (Exception e) {
                            return "";
                        }
                    }))
                    .map(resource -> {
                        try {
                            return ReliabilityObjectMappers.json().readValue(resource.getInputStream(), type);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read resource " + resource, e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve resources: " + pattern, e);
        }
    }

    // 특정 classpath 패턴의 YAML 파일 목록 적재
    private <T> List<T> readYamlDirectory(String pattern, Class<T> type) {
        try {
            Resource[] resources = resolver.getResources(pattern);
            return Arrays.stream(resources)
                    .sorted(Comparator.comparing(resource -> {
                        try {
                            return resource.getFilename();
                        } catch (Exception e) {
                            return "";
                        }
                    }))
                    .map(resource -> {
                        try {
                            return ReliabilityObjectMappers.yaml().readValue(resource.getInputStream(), type);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read resource " + resource, e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve resources: " + pattern, e);
        }
    }
}
