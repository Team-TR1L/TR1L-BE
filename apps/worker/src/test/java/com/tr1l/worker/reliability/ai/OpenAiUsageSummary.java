package com.tr1l.worker.reliability.ai;

// 토큰 사용량 요약
public record OpenAiUsageSummary(
        long inputTokens,
        long outputTokens,
        long totalTokens
) {
}
