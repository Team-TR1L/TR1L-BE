package com.tr1l.worker.reliability;

import com.fasterxml.jackson.databind.JsonNode;
import com.tr1l.worker.reliability.ai.AiArtifactWriter;
import com.tr1l.worker.reliability.ai.GeneratedInvariantBatchEvaluation;
import com.tr1l.worker.reliability.ai.GeneratedInvariantCandidate;
import com.tr1l.worker.reliability.ai.GeneratedInvariantParser;
import com.tr1l.worker.reliability.ai.GeneratedInvariantScorer;
import com.tr1l.worker.reliability.ai.Job1InvariantContextPackBuilder;
import com.tr1l.worker.reliability.ai.Job1InvariantPrompt;
import com.tr1l.worker.reliability.ai.Job1InvariantPromptComposer;
import com.tr1l.worker.reliability.ai.OpenAiInvariantGenerationResult;
import com.tr1l.worker.reliability.ai.OpenAiApiRequestException;
import com.tr1l.worker.reliability.ai.OpenAiInvariantRuntimeConfig;
import com.tr1l.worker.reliability.ai.OpenAiResponsesInvariantGenerator;
import com.tr1l.worker.reliability.support.AllureEvidenceWriter;
import com.tr1l.worker.reliability.support.ReliabilityObjectMappers;
import com.tr1l.worker.reliability.support.ReliabilityResourceCatalog;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("job1-ai-live")
// OpenAI 실호출 검증
class Job1AiInvariantLiveGenerationTest {
    private final Job1InvariantContextPackBuilder contextPackBuilder = new Job1InvariantContextPackBuilder();
    private final Job1InvariantPromptComposer promptComposer = new Job1InvariantPromptComposer();
    private final GeneratedInvariantParser parser = new GeneratedInvariantParser();
    private final GeneratedInvariantScorer scorer = new GeneratedInvariantScorer();
    private final ReliabilityResourceCatalog catalog = new ReliabilityResourceCatalog();
    private final AiArtifactWriter artifactWriter = new AiArtifactWriter();
    private final AllureEvidenceWriter evidenceWriter = new AllureEvidenceWriter();

    @Test
    @DisplayName("OpenAI gpt-5.4-mini로 Job1 invariant 후보를 생성하고 캡처 저장 보기")
    void liveGenerationShouldCaptureOpenAiResponse() throws Exception {
        // live 호출 조건 확인
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("JOB1_AI_INVARIANT_LIVE_ENABLED")),
                "Set JOB1_AI_INVARIANT_LIVE_ENABLED=true to run OpenAI live generation"
        );

        OpenAiInvariantRuntimeConfig config = OpenAiInvariantRuntimeConfig.fromEnvironment();
        Assumptions.assumeTrue(config.enabled(), "Set OPENAI_API_KEY to run OpenAI live generation");

        Job1InvariantPrompt prompt = promptComposer.compose(contextPackBuilder.build());
        OpenAiResponsesInvariantGenerator generator = new OpenAiResponsesInvariantGenerator(config);

        long inputTokenCount;
        OpenAiInvariantGenerationResult generationResult;

        try {
            inputTokenCount = generator.countInputTokens(prompt);
            generationResult = generator.generate(prompt);
        } catch (OpenAiApiRequestException e) {
            // 실패 요청 캡처 저장
            artifactWriter.writeText(config.runName(), "failed-request-body.json", e.requestBody());
            artifactWriter.writeText(config.runName(), "failed-response-body.json", e.responseBody());
            artifactWriter.writeJson(
                    config.runName(),
                    "failed-response-meta.json",
                    Map.of(
                            "path", e.path(),
                            "statusCode", e.statusCode()
                    )
            );
            evidenceWriter.attachText("failed-request-body.json", e.requestBody());
            evidenceWriter.attachText("failed-response-body.json", e.responseBody());
            throw e;
        }

        List<GeneratedInvariantCandidate> candidates = parser.parse(generationResult.outputText());
        GeneratedInvariantBatchEvaluation evaluation = scorer.evaluate(candidates, catalog.loadActiveInvariants());

        Path requestPath = artifactWriter.writeText(config.runName(), "request-body.json", generationResult.requestBody());
        Path responsePath = artifactWriter.writeText(config.runName(), "response-body.json", generationResult.responseBody());
        Path outputTextPath = artifactWriter.writeText(config.runName(), "response-output-text.json", prettyJson(generationResult.outputText()));
        Path candidatesPath = artifactWriter.writeJson(config.runName(), "generated-candidates.json", candidates);
        Path evaluationPath = artifactWriter.writeJson(config.runName(), "generated-candidate-evaluation.json", evaluation);
        Path metricsPath = artifactWriter.writeJson(
                config.runName(),
                "generation-metrics.json",
                Map.of(
                        "requestedModel", config.model(),
                        "responseModel", generationResult.model(),
                        "responseId", generationResult.responseId(),
                        "inputTokenCount", inputTokenCount,
                        "usage", generationResult.usage(),
                        "candidateCount", candidates.size(),
                        "activeCandidateCount", evaluation.activeCandidateCount(),
                        "reviewRequiredCount", evaluation.reviewRequiredCount(),
                        "rejectCount", evaluation.rejectCount(),
                        "averageScore", evaluation.averageScore()
                )
        );

        evidenceWriter.attachText("request-body.json", generationResult.requestBody());
        evidenceWriter.attachText("response-body.json", generationResult.responseBody());
        evidenceWriter.attachJson("generated-candidates.json", candidates);
        evidenceWriter.attachJson("generated-candidate-evaluation.json", evaluation);

        assertThat(generationResult.responseId()).isNotBlank();
        assertThat(generationResult.model()).startsWith(config.model());
        assertThat(inputTokenCount).isPositive();
        assertThat(candidates).isNotEmpty();
        assertThat(evaluation.totalCandidates()).isEqualTo(candidates.size());
        assertThat(requestPath).exists();
        assertThat(responsePath).exists();
        assertThat(outputTextPath).exists();
        assertThat(candidatesPath).exists();
        assertThat(evaluationPath).exists();
        assertThat(metricsPath).exists();
    }

    // output text pretty 출력
    private String prettyJson(String rawJson) throws Exception {
        JsonNode root = ReliabilityObjectMappers.json().readTree(rawJson);
        return ReliabilityObjectMappers.json().writeValueAsString(root);
    }
}
