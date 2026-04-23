package com.tr1l.worker.reliability.ai;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// Job1 입력 묶음 정의
public record Job1InvariantContextPack(
        String packId,
        List<ContextSource> sources
) {
    public Job1InvariantContextPack {
        packId = requireText(packId, "packId");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources is required"));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources is empty");
        }
    }

    // 사용자 프롬프트 렌더링
    public String renderForUserPrompt() {
        return sources.stream()
                .map(source -> "=== " + source.title() + " ===\n" + source.content().trim())
                .collect(Collectors.joining("\n\n"));
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
