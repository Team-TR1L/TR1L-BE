package com.tr1l.worker.reliability.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

// Responses API 호출기
public final class OpenAiResponsesInvariantGenerator {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final OpenAiInvariantRuntimeConfig config;

    public OpenAiResponsesInvariantGenerator(OpenAiInvariantRuntimeConfig config) {
        this.config = config;
    }

    public long countInputTokens(Job1InvariantPrompt prompt) {
        ObjectNode requestBody = buildInputTokenCountRequestBody(prompt);
        String responseBody = send("/responses/input_tokens", requestBody);

        try {
            JsonNode root = ReliabilityObjectMappers.json().readTree(responseBody);
            return root.path("input_tokens").asLong(-1);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse input token count response", e);
        }
    }

    public OpenAiInvariantGenerationResult generate(Job1InvariantPrompt prompt) {
        ObjectNode requestBody = buildGenerationRequestBody(prompt);

        String requestBodyText = toPrettyJson(requestBody);
        String responseBody = send("/responses", requestBody);

        try {
            JsonNode root = ReliabilityObjectMappers.json().readTree(responseBody);
            return new OpenAiInvariantGenerationResult(
                    root.path("id").asText(""),
                    root.path("model").asText(config.model()),
                    requestBodyText,
                    toPrettyJson(root),
                    extractOutputText(root),
                    new OpenAiUsageSummary(
                            root.path("usage").path("input_tokens").asLong(-1),
                            root.path("usage").path("output_tokens").asLong(-1),
                            root.path("usage").path("total_tokens").asLong(-1)
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse OpenAI responses payload", e);
        }
    }

    // input token 계산용 요청 바디
    ObjectNode buildInputTokenCountRequestBody(Job1InvariantPrompt prompt) {
        return baseConversationRequestBody(prompt);
    }

    // 응답 생성용 요청 바디
    ObjectNode buildGenerationRequestBody(Job1InvariantPrompt prompt) {
        ObjectNode requestBody = baseConversationRequestBody(prompt);

        ObjectNode reasoning = requestBody.putObject("reasoning");
        reasoning.put("effort", config.reasoningEffort());

        requestBody.put("max_output_tokens", 3000);
        requestBody.set("text", buildStructuredOutputFormat());
        return requestBody;
    }

    // 공통 대화 바디
    private ObjectNode baseConversationRequestBody(Job1InvariantPrompt prompt) {
        ObjectNode requestBody = ReliabilityObjectMappers.json().createObjectNode();
        requestBody.put("model", config.model());

        ArrayNode input = requestBody.putArray("input");
        ObjectNode systemMessage = input.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", prompt.systemPrompt());

        ObjectNode userMessage = input.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt.userPrompt());
        return requestBody;
    }

    // structured output 정의
    private ObjectNode buildStructuredOutputFormat() {
        ObjectNode text = ReliabilityObjectMappers.json().createObjectNode();
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", "job1_invariant_candidates");
        format.put("strict", true);

        ObjectNode schema = format.putObject("schema");
        schema.put("type", "object");
        ObjectNode schemaProperties = schema.putObject("properties");
        ObjectNode invariants = schemaProperties.putObject("invariants");
        invariants.put("type", "array");
        ArrayNode requiredRoot = schema.putArray("required");
        requiredRoot.add("invariants");
        schema.put("additionalProperties", false);

        ObjectNode item = invariants.putObject("items");
        item.put("type", "object");

        ObjectNode properties = item.putObject("properties");
        stringProperty(properties, "id", "invariant id");
        enumProperty(properties, "category", "state_consistency", "count_consistency", "temporal", "referential");
        stringProperty(properties, "description", "description");
        enumProperty(properties, "scope", "always", "rerun_after_crash", "step_complete");
        stringProperty(properties, "sql_check", "sql check");
        stringProperty(properties, "violated_by", "violation scenario");
        enumProperty(properties, "severity", "critical", "major", "minor");

        ArrayNode required = item.putArray("required");
        required.add("id");
        required.add("category");
        required.add("description");
        required.add("scope");
        required.add("sql_check");
        required.add("violated_by");
        required.add("severity");
        item.put("additionalProperties", false);

        return text;
    }

    // 문자열 속성 정의
    private void stringProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
    }

    // enum 속성 정의
    private void enumProperty(ObjectNode properties, String name, String... values) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        ArrayNode enumValues = property.putArray("enum");
        for (String value : values) {
            enumValues.add(value);
        }
    }

    // 응답 전송
    private String send(String path, JsonNode requestBody) {
        try {
            String requestBodyText = toPrettyJson(requestBody);
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyText, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new OpenAiApiRequestException(path, response.statusCode(), requestBodyText, response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call OpenAI responses API", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call OpenAI responses API", e);
        }
    }

    // 텍스트 추출
    private String extractOutputText(JsonNode root) {
        String outputText = root.path("output_text").asText("");
        if (!outputText.isBlank()) {
            return outputText;
        }

        for (JsonNode outputItem : root.path("output")) {
            for (JsonNode contentItem : outputItem.path("content")) {
                if ("output_text".equals(contentItem.path("type").asText())
                        && !contentItem.path("text").asText("").isBlank()) {
                    return contentItem.path("text").asText();
                }
            }
        }

        throw new IllegalStateException("OpenAI response does not contain output_text");
    }

    // json pretty 출력
    private String toPrettyJson(Object value) {
        try {
            return ReliabilityObjectMappers.json().writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }
}
