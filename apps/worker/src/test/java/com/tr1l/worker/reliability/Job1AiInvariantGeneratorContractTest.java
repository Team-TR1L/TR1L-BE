package com.tr1l.worker.reliability;

import com.tr1l.worker.reliability.ai.GeneratedInvariantCandidate;
import com.tr1l.worker.reliability.ai.GeneratedInvariantParser;
import com.tr1l.worker.reliability.ai.Job1InvariantContextPack;
import com.tr1l.worker.reliability.ai.Job1InvariantContextPackBuilder;
import com.tr1l.worker.reliability.ai.Job1InvariantPrompt;
import com.tr1l.worker.reliability.ai.Job1InvariantPromptComposer;
import com.tr1l.worker.reliability.support.ReliabilityResourceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// AI invariant generator 초안 계약 검증
class Job1AiInvariantGeneratorContractTest {
    private static final String SAMPLE_RESPONSE_PATH =
            "reliability/invariants/generated/job1-invariant-candidates.sample.json";

    private final Job1InvariantContextPackBuilder contextPackBuilder = new Job1InvariantContextPackBuilder();
    private final Job1InvariantPromptComposer promptComposer = new Job1InvariantPromptComposer();
    private final GeneratedInvariantParser parser = new GeneratedInvariantParser();
    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();

    @Test
    @DisplayName("Job1 invariant 입력 묶음에 판단 재료가 빠지지 않는지 보기")
    void job1ContextPackShouldContainRequiredSections() {
        // 컨텍스트 묶음 누락 방지
        Job1InvariantContextPack contextPack = contextPackBuilder.build();

        assertThat(contextPack.packId()).isEqualTo("job1-invariant-generator-mvp");
        assertThat(contextPack.sources()).hasSize(6);
        assertThat(contextPack.renderForUserPrompt())
                .contains("billing_targets")
                .contains("billing_work")
                .contains("billing_snapshot")
                .contains("TARGET -> PROCESSING")
                .contains("snapshotSavePort.saveAll")
                .contains("재실행 완료 뒤");
    }

    @Test
    @DisplayName("LLM 요청 초안이 응답 규칙과 컨텍스트를 같이 묶는지 보기")
    void promptDraftShouldContainSystemRulesAndUserContext() {
        // 시스템 지시문과 사용자 입력 묶음 확인
        Job1InvariantPrompt prompt = promptComposer.compose(contextPackBuilder.build());

        assertThat(prompt.systemPrompt())
                .contains("JSON 객체 하나만 출력")
                .contains("\"invariants\"")
                .contains("\"sql_check\"")
                .contains("PROCESSING 상태");
        assertThat(prompt.userPrompt())
                .contains("=== billing_targets 테이블 DDL ===")
                .contains("=== Step3 핵심 코드 ===")
                .contains("응답은 JSON 객체 하나만 반환")
                .contains("최상위 필드는 invariants");
    }

    @Test
    @DisplayName("샘플 응답 JSON이 생성 후보 스키마로 읽히는지 보기")
    void sampleResponseShouldParseToGeneratedInvariantCandidates() {
        // 샘플 응답 스키마 파싱 검증
        List<GeneratedInvariantCandidate> candidates = parser.loadFromResource(SAMPLE_RESPONSE_PATH);

        assertThat(candidates).hasSize(4);
        assertThat(candidates).extracting(GeneratedInvariantCandidate::id)
                .containsExactly("INV-101", "INV-102", "INV-103", "INV-104");
        assertThat(candidates).extracting(GeneratedInvariantCandidate::category)
                .containsExactly("count_consistency", "state_consistency", "temporal", "referential");
        assertThat(candidates).filteredOn(GeneratedInvariantCandidate::crossDb).hasSize(1);
    }

    @Test
    @DisplayName("코드펜스로 감싼 응답도 그대로 읽는지 보기")
    void parserShouldAcceptCodeFencedJsonResponse() {
        // 응답 포맷 흔들림 허용
        String fencedResponse = """
                ```json
                %s
                ```
                """.formatted(loader.loadText(SAMPLE_RESPONSE_PATH).trim());

        List<GeneratedInvariantCandidate> candidates = parser.parse(fencedResponse);

        assertThat(candidates).hasSize(4);
        assertThat(candidates.get(3).crossDb()).isTrue();
    }

    @Test
    @DisplayName("코드펜스 앞뒤 설명이 붙어도 JSON 본문만 뽑는지 보기")
    void parserShouldExtractCodeFenceEvenWithSurroundingText() {
        // 응답 앞뒤 설명 텍스트 허용
        String fencedResponseWithProse = """
                아래는 요청한 invariant 후보입니다

                ```json
                %s
                ```

                필요하면 다음 단계에서 mutant 도 만들 수 있습니다
                """.formatted(loader.loadText(SAMPLE_RESPONSE_PATH).trim());

        List<GeneratedInvariantCandidate> candidates = parser.parse(fencedResponseWithProse);

        assertThat(candidates).hasSize(4);
        assertThat(candidates).extracting(GeneratedInvariantCandidate::id)
                .containsExactly("INV-101", "INV-102", "INV-103", "INV-104");
    }

    @Test
    @DisplayName("배열 형태 legacy 응답도 같은 후보 목록으로 읽는지 보기")
    void parserShouldAcceptLegacyArrayResponse() {
        // legacy 배열 응답 호환
        String legacyArrayResponse = """
                [
                  {
                    "id": "INV-101",
                    "category": "count_consistency",
                    "description": "legacy 배열 응답",
                    "scope": "step_complete",
                    "sql_check": "SELECT 1",
                    "violated_by": "legacy path",
                    "severity": "critical"
                  }
                ]
                """;

        List<GeneratedInvariantCandidate> candidates = parser.parse(legacyArrayResponse);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo("INV-101");
    }

    @Test
    @DisplayName("허용되지 않은 scope 값이면 파싱 단계에서 바로 막는지 보기")
    void parserShouldRejectInvalidScopeValue() {
        // 후처리 검증 경계 확인
        String invalidResponse = """
                [
                  {
                    "id": "INV-999",
                    "category": "temporal",
                    "description": "잘못된 scope 샘플",
                    "scope": "after_rerun",
                    "sql_check": "SELECT 1",
                    "violated_by": "scope 오기입",
                    "severity": "major"
                  }
                ]
                """;

        assertThatThrownBy(() -> parser.parse(invalidResponse))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse generated invariant response");
    }
}
