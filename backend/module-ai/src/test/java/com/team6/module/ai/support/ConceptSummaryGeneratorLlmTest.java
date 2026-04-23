package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConceptSummaryGeneratorLlmTest {

    @Test
    void generate_prefersLlmBulletsAndSpecialRequests() {
        GuideRecommendRequest req = GuideRecommendRequest.builder()
                .region("제주")
                .durationDays(2)
                .llmGuideBullets(List.of("맛집 위주"))
                .llmSpecialRequests("애매한 일정 조정 가능해")
                .build();

        assertThat(ConceptSummaryGenerator.generate(req)).isEqualTo("• 맛집 위주\n\n애매한 일정 조정 가능해");
    }

    @Test
    void generateMatchRequestConcept_doesNotDuplicateLlmCopy() {
        GuideRecommendRequest req = GuideRecommendRequest.builder()
                .region("부산")
                .llmGuideBullets(List.of("야경 좋아해"))
                .durationDays(1)
                .build();

        String concept = ConceptSummaryGenerator.generateMatchRequestConcept(req);
        assertThat(concept).contains("부산 여행");
        assertThat(concept).doesNotContain("야경 좋아해");
    }
}
