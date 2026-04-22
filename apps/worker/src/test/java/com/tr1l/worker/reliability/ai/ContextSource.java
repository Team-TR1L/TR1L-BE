package com.tr1l.worker.reliability.ai;

import java.util.Objects;

// 컨텍스트 조각 정의
public record ContextSource(
        String id,
        String title,
        String classpathLocation,
        String content
) {
    public ContextSource {
        id = requireText(id, "id");
        title = requireText(title, "title");
        classpathLocation = requireText(classpathLocation, "classpathLocation");
        content = requireText(content, "content");
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
