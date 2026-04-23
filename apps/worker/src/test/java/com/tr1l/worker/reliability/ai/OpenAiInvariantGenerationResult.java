package com.tr1l.worker.reliability.ai;

// OpenAI 응답 결과 묶음
public record OpenAiInvariantGenerationResult(
        String responseId,
        String model,
        String requestBody,
        String responseBody,
        String outputText,
        OpenAiUsageSummary usage
) {
}
