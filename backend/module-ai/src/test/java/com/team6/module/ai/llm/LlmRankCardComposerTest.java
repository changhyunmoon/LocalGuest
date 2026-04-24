package com.team6.module.ai.llm;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmRankCardComposerTest {

    @Test
    void secondFeedIndex_isDeterministicInRange() {
        assertThat(LlmRankCardComposer.secondFeedIndex(3, 0L)).isEqualTo(1);
        assertThat(LlmRankCardComposer.secondFeedIndex(3, 1L)).isEqualTo(2);
        assertThat(LlmRankCardComposer.secondFeedIndex(3, 2L)).isEqualTo(1);
        assertThat(LlmRankCardComposer.secondFeedIndex(1, 99L)).isEqualTo(-1);
    }

    @Test
    void sortByQualityForPrompt_ordersBySignal() {
        List<GuideRecommendRequest.GuideCandidateDto> list = List.of(
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(1L)
                        .reviewCount(1)
                        .averageRating(BigDecimal.valueOf(4.5))
                        .publicFeedCount(1)
                        .build(),
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(2L)
                        .reviewCount(50)
                        .averageRating(BigDecimal.valueOf(4.9))
                        .publicFeedCount(10)
                        .build()
        );
        List<GuideRecommendRequest.GuideCandidateDto> sorted = LlmRankCardComposer.sortByQualityForPrompt(list);
        assertThat(sorted.get(0).getGuideId()).isEqualTo(2L);
        assertThat(sorted.get(1).getGuideId()).isEqualTo(1L);
    }
}
