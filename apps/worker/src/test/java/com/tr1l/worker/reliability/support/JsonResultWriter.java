package com.tr1l.worker.reliability.support;

import com.tr1l.worker.reliability.scenario.Job1ScenarioDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// build 하위 결과 json 저장
public final class JsonResultWriter {
    private final Path baseDir = Paths.get("build", "job1-reliability-results");

    public Path write(Job1ScenarioDefinition scenario, String fileName, Object value) {
        Path dir = scenarioDirectory(scenario);
        try {
            Files.createDirectories(dir);
            Path output = dir.resolve(fileName);
            Files.writeString(output, ReliabilityObjectMappers.json().writeValueAsString(value), StandardCharsets.UTF_8);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write scenario artifact: " + fileName, e);
        }
    }

    public Path scenarioDirectory(Job1ScenarioDefinition scenario) {
        return baseDir.resolve(scenario.artifactDirectoryName());
    }
}
