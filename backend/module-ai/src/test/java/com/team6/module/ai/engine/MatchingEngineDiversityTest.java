package com.team6.module.ai.engine;

import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.FeedbackMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.AiRecommendationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Top-N 다양성(유사도 패널티) 스모크. 희망 지역 권역 유사도는 점수만으로는 구분되지 않으므로
 * 엔진이 예외 없이 결과를 내는지·첫 후보가 최고 base 점수인지 검증한다.
 */
class MatchingEngineDiversityTest {

    private static MatchingEngine engine() {
        LocalGuestAiProperties aiProps = new LocalGuestAiProperties();
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(aiProps);
        ScoreCalculator scoreCalculator = new ScoreCalculator(
                new RegionMatchPolicy(adjacent),
                new StyleMatchPolicy(),
                new BudgetMatchPolicy(),
                new ActivityMatchPolicy(),
                new LanguageMatchPolicy(),
                new FeedbackMatchPolicy(),
                new AiRecommendationMetrics(new SimpleMeterRegistry())
        );
        return new MatchingEngine(scoreCalculator, new ReasonGenerator(adjacent), adjacent);
    }

    @Test
    void recommend_should_pick_highest_base_score_first_when_topN_greater_than_one() {
        TravelerPreference pref = TravelerPreference.builder()
                .region("강릉")
                .travelStyle("힐링")
                .budgetLevel("낮음")
                .activityTags(List.of("산책", "바다"))
                .preferredLanguages(List.of("한국어"))
                .build();

        List<GuideAiProfile> guides = List.of(
                GuideAiProfile.builder()
                        .guideId(1L)
                        .guideName("속초A")
                        .region("속초")
                        .guideStyle("힐링")
                        .priceLevel("낮음")
                        .specialtyTags(List.of("산책", "바다", "카페"))
                        .languages(List.of("한국어"))
                        .build(),
                GuideAiProfile.builder()
                        .guideId(2L)
                        .guideName("동해B")
                        .region("동해")
                        .guideStyle("힐링")
                        .priceLevel("낮음")
                        .specialtyTags(List.of("산책", "바다", "카페"))
                        .languages(List.of("한국어"))
                        .build(),
                GuideAiProfile.builder()
                        .guideId(3L)
                        .guideName("강릉C")
                        .region("강릉")
                        .guideStyle("힐링")
                        .priceLevel("낮음")
                        .specialtyTags(List.of("산책", "바다", "맛집"))
                        .languages(List.of("한국어"))
                        .build()
        );

        GuideRecommendResponse response = engine().recommend(pref, guides, 2);

        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().get(0).getGuideId()).isEqualTo(3L);
        assertThat(response.getRecommendations().get(0).getScore())
                .isGreaterThanOrEqualTo(response.getRecommendations().get(1).getScore());
    }
}
