package com.tr1l.worker.reliability.ai;

// OpenAI 호출 설정 묶음
public record OpenAiInvariantRuntimeConfig(
        String apiKey,
        String baseUrl,
        String model,
        String reasoningEffort,
        String runName
) {
    public static OpenAiInvariantRuntimeConfig fromEnvironment() {
        return new OpenAiInvariantRuntimeConfig(
                env("OPENAI_API_KEY", ""),
                env("OPENAI_API_BASE_URL", "https://api.openai.com/v1"),
                env("JOB1_AI_INVARIANT_OPENAI_MODEL", "gpt-5.4-mini"),
                env("JOB1_AI_INVARIANT_REASONING_EFFORT", "low"),
                env("JOB1_AI_INVARIANT_RUN_NAME", "live-job1-invariant-generator")
        );
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
