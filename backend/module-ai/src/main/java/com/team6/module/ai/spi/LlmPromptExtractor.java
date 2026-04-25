package com.team6.module.ai.spi;

import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.util.List;
import java.util.Optional;

/**
 * 자연어 프롬프트를 {@link GuideRecommendRequest}로 바꾸는 LLM 기반 추출기 SPI.
 * <p>구현체는 {@code module-openai} 등에서 제공하며, 비어 있으면({@link Optional#empty()}) 호출부가
 * 룰 기반 {@link com.team6.module.ai.parser.PromptParser}로 폴백한다.
 */
@FunctionalInterface
public interface LlmPromptExtractor {

    /**
     * @return 추출 성공 시 값, 아니면 empty(호출부에서 룰 파서 사용)
     */
    Optional<GuideRecommendRequest> tryExtract(
            String prompt,
            int topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    );
}
