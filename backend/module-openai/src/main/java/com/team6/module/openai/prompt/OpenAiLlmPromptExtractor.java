package com.team6.module.openai.prompt;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.spi.LlmPromptExtractor;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.List;
import java.util.Optional;

/**
 * OpenAI 기반 프롬프트 추출기. 현재는 스텁({@link Optional#empty()})으로 두고,
 * 호출부({@code PromptRecommendationService})가 룰 파서로 폴백한다.
 */
public final class OpenAiLlmPromptExtractor implements LlmPromptExtractor {

    @SuppressWarnings("unused")
    private final OpenAiChatModel chatModel;

    public OpenAiLlmPromptExtractor(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Optional<GuideRecommendRequest> tryExtract(
            String prompt,
            int topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        return Optional.empty();
    }
}
