package com.tr1l.worker.reliability.support;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// 테스트 리소스 로드 전용
public final class ReliabilityResourceLoader {

    public <T> T loadJson(String classpathLocation, Class<T> type) {
        try (InputStream inputStream = open(classpathLocation)) {
            return ReliabilityObjectMappers.json().readValue(inputStream, type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load JSON resource: " + classpathLocation, e);
        }
    }

    public <T> T loadYaml(String classpathLocation, Class<T> type) {
        try (InputStream inputStream = open(classpathLocation)) {
            return ReliabilityObjectMappers.yaml().readValue(inputStream, type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load YAML resource: " + classpathLocation, e);
        }
    }

    public String loadText(String classpathLocation) {
        try (InputStream inputStream = open(classpathLocation)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load text resource: " + classpathLocation, e);
        }
    }

    public boolean exists(String classpathLocation) {
        return new ClassPathResource(classpathLocation).exists();
    }

    private InputStream open(String classpathLocation) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("Classpath resource not found: " + classpathLocation);
        }
        return resource.getInputStream();
    }
}
