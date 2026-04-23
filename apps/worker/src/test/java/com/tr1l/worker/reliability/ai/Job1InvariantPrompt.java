package com.tr1l.worker.reliability.ai;

import java.util.Objects;

// LLM 요청 초안 정의
public record Job1InvariantPrompt(
        String systemPrompt,
        String userPrompt
) {
    public Job1InvariantPrompt {
        systemPrompt = requireText(systemPrompt, "systemPrompt");
        userPrompt = requireText(userPrompt, "userPrompt");
    }

    // 필수값 방어
    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is blank");
        }
        return trimmed;
    }
}
