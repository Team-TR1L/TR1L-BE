package com.tr1l.worker.reliability.ai;

import com.tr1l.worker.reliability.support.ReliabilityResourceLoader;

// 프롬프트 조립 전용
public final class Job1InvariantPromptComposer {
    private static final String SYSTEM_PROMPT_PATH = "reliability/ai/prompts/job1-invariant-generator-system.txt";

    private final ReliabilityResourceLoader loader = new ReliabilityResourceLoader();

    public Job1InvariantPrompt compose(Job1InvariantContextPack contextPack) {
        String systemPrompt = loader.loadText(SYSTEM_PROMPT_PATH).trim();
        String userPrompt = """
                다음 컨텍스트를 읽고 Job1 Step3 rerun safe invariant 후보를 도출하라

                %s

                응답은 JSON 배열만 반환
                """.formatted(contextPack.renderForUserPrompt()).trim();

        return new Job1InvariantPrompt(systemPrompt, userPrompt);
    }
}
