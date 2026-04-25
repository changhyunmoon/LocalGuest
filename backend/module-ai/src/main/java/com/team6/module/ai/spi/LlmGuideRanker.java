package com.team6.module.ai.spi;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.llm.LlmGuideRankResult;

import java.util.List;
import java.util.Optional;

/**
 * 지역 등으로 좁혀진 후보에 대해 사용자 프롬프트 뉘앙스에 맞게 순위를 매긴다.
 * <p>구현체는 {@code module-openai}에서 제공하며, 비어 있으면 룰 엔진 결과만 사용한다.
 */
public interface LlmGuideRanker {

    /**
     * @param userPrompt   사용자 원문(상위에서 길이 제한 가능)
     * @param candidates   후보 풀(동일 요청의 {@link GuideRecommendRequest#getGuideCandidates()})
     * @param topN         반환할 상위 개수
     * @param tieBreakSeed 피드 2번째 선택 등 결정론적 보조에 사용
     * @return 검증된 순서(후보 id의 부분집합). 실패 시 empty
     */
    Optional<LlmGuideRankResult> tryRank(
            String userPrompt,
            List<GuideRecommendRequest.GuideCandidateDto> candidates,
            int topN,
            long tieBreakSeed
    );
}
