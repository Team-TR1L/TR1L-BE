package com.tr1l.worker.reliability.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// OpenAI 요청 바디 계약 검증
class OpenAiResponsesInvariantGeneratorContractTest {
    private final Job1InvariantContextPackBuilder contextPackBuilder = new Job1InvariantContextPackBuilder();
    private final Job1InvariantPromptComposer promptComposer = new Job1InvariantPromptComposer();
    private final OpenAiResponsesInvariantGenerator generator = new OpenAiResponsesInvariantGenerator(
            new OpenAiInvariantRuntimeConfig(
                    "test-key",
                    "https://api.openai.com/v1",
                    "gpt-5.4-mini",
                    "low",
                    "test-run"
            )
    );

    @Test
    @DisplayName("input token 계산 바디에는 최소 필드만 담는지 보기")
    void inputTokenCountRequestShouldContainOnlyMinimalFields() {
        // input token 요청 최소 바디 확인
        Job1InvariantPrompt prompt = promptComposer.compose(contextPackBuilder.build());
        var requestBody = generator.buildInputTokenCountRequestBody(prompt);

        assertThat(requestBody.has("store")).isFalse();
        assertThat(requestBody.has("text")).isFalse();
        assertThat(requestBody.has("reasoning")).isFalse();
        assertThat(requestBody.has("max_output_tokens")).isFalse();
        assertThat(requestBody.path("model").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(requestBody.path("input").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("응답 생성 바디에는 structured output 과 reasoning 을 같이 담는지 보기")
    void generationRequestShouldContainStructuredOutputFields() {
        // 응답 생성 요청 바디 확인
        Job1InvariantPrompt prompt = promptComposer.compose(contextPackBuilder.build());
        var requestBody = generator.buildGenerationRequestBody(prompt);

        assertThat(requestBody.path("reasoning").path("effort").asText()).isEqualTo("low");
        assertThat(requestBody.path("max_output_tokens").asInt()).isEqualTo(3000);
        assertThat(requestBody.path("text").path("format").path("type").asText())
                .isEqualTo("json_schema");
        assertThat(requestBody.path("text").path("format").path("schema").path("type").asText())
                .isEqualTo("object");
        assertThat(requestBody.path("text").path("format").path("schema").path("properties").path("invariants").path("type").asText())
                .isEqualTo("array");
    }
}
