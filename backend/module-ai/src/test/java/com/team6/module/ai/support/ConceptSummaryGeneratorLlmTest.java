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

        assertThat(ConceptSummaryGenerator.generate(req)).isEqualTo("• 맛집 위주\n애매한 일정 조정 가능해");
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

    @Test
    void formatLlmGuideCopy_dedupes_bullets_against_specialRequests() {
        String out = ConceptSummaryGenerator.formatLlmGuideCopy(
                List.of("제주 반시계 방향으로 오름 투어", "마지막에 한라산(추정) 방문", "제주 반시계 방향으로 오름 투어"),
                "2박 3일 동안 제주 반시계 방향으로 오름 투어를 하고 마지막에 한라산(추정)에 가고 싶어."
        );

        // specialRequests에 포함된 불릿은 제거되고, 중복 불릿도 제거된다.
        assertThat(out).doesNotContain("• 제주 반시계 방향으로 오름 투어");
        assertThat(out).contains("• 마지막에 한라산(추정) 방문");
    }
}
