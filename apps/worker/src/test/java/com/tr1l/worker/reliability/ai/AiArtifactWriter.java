package com.tr1l.worker.reliability.ai;

import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// AI 결과 파일 저장기
public final class AiArtifactWriter {
    private final Path baseDir = Paths.get("build", "job1-ai-invariant-results");

    public Path writeJson(String runName, String fileName, Object value) {
        Path dir = directory(runName);
        try {
            Files.createDirectories(dir);
            Path output = dir.resolve(fileName);
            Files.writeString(output, ReliabilityObjectMappers.json().writeValueAsString(value), StandardCharsets.UTF_8);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write AI json artifact: " + fileName, e);
        }
    }

    public Path writeText(String runName, String fileName, String value) {
        Path dir = directory(runName);
        try {
            Files.createDirectories(dir);
            Path output = dir.resolve(fileName);
            Files.writeString(output, value, StandardCharsets.UTF_8);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write AI text artifact: " + fileName, e);
        }
    }

    public Path directory(String runName) {
        return baseDir.resolve(runName);
    }
}
