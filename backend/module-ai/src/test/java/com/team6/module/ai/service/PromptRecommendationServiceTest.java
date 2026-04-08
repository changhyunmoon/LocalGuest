package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.engine.MatchingEngine;
import com.team6.module.ai.engine.ReasonGenerator;
import com.team6.module.ai.engine.ScoreCalculator;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptRecommendationServiceTest {

    private PromptRecommendationService createService() {
        ScoreCalculator scoreCalculator = new ScoreCalculator(
                new RegionMatchPolicy(),
                new StyleMatchPolicy(),
                new BudgetMatchPolicy(),
                new ActivityMatchPolicy(),
                new LanguageMatchPolicy()
        );
        AiRecommendationService aiRecommendationService =
                new AiRecommendationServiceImpl(new MatchingEngine(scoreCalculator, new ReasonGenerator()));

        return new PromptRecommendationService(new PromptParser(), aiRecommendationService);
    }

    @Test
    void recommendByPrompt_should_return_conceptSummary_and_keywords() {
        PromptRecommendationService service = createService();

        GuideRecommendResponse response = service.recommendByPrompt(
                "제주 2박3일 4명 여행인데 오션뷰랑 맛집 위주로 추천해줘. 술집은 빼고!",
                3,
                List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("제주바다")
                                .region("제주")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("바다", "맛집", "카페"))
                                .languages(List.of("한국어"))
                                .build()
                )
        );

        assertThat(response.getConceptSummary()).isNotBlank();
        assertThat(response.getKeywords()).isNotNull();
        assertThat(response.getKeywords().getRegion()).isEqualTo("제주");
        assertThat(response.getKeywords().getDurationDays()).isEqualTo(3);
        assertThat(response.getKeywords().getHeadcount()).isEqualTo(4);
        assertThat(response.getKeywords().getActivityTags()).contains("바다", "맛집");
        assertThat(response.getKeywords().getExcludedActivityTags()).contains("술집");
    }
}

