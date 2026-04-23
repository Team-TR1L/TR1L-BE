package com.tr1l.worker.reliability.ai;

import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceLoader;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 생성 응답 파싱 전용
public final class GeneratedInvariantParser {
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL);

    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();

    public List<GeneratedInvariantCandidate> loadFromResource(String classpathLocation) {
        return parse(loader.loadText(classpathLocation));
    }

    public List<GeneratedInvariantCandidate> parse(String raw) {
        String cleaned = stripCodeFence(raw);
        try {
            JsonNode root = ReliabilityObjectMappers.json().readTree(cleaned);
            JsonNode candidateArray = unwrapCandidates(root);
            GeneratedInvariantCandidate[] parsed =
                    ReliabilityObjectMappers.json().treeToValue(candidateArray, GeneratedInvariantCandidate[].class);
            return Arrays.asList(parsed);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse generated invariant response", e);
        }
    }

    // 배열 또는 wrapper 객체 허용
    private JsonNode unwrapCandidates(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        if (root.isObject() && root.path("invariants").isArray()) {
            return root.path("invariants");
        }
        throw new IllegalStateException("Failed to parse generated invariant response");
    }

    // 코드펜스 제거
    private String stripCodeFence(String raw) {
        String trimmed = Objects.requireNonNull(raw, "raw is required").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("raw is blank");
        }

        Matcher matcher = CODE_FENCE.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return trimmed;
    }
}
