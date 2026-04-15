package com.team6.module.ai.engine;

import com.team6.module.ai.config.DiversityRerankSnapshot;
import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.policy.ComboMatchPolicy;
import com.team6.module.ai.dto.response.GuideRecommendItem;
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
import com.team6.module.ai.support.AiRecommendationTuning;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Top-N 다양성(유사도 패널티) 스모크. 희망 지역 권역 유사도는 점수만으로는 구분되지 않으므로
 * 엔진이 예외 없이 결과를 내는지·첫 후보가 최고 base 점수인지 검증한다.
 */
class MatchingEngineDiversityTest {

    private static MatchingEngine engine(SimpleMeterRegistry registry) {
        LocalGuestAiProperties aiProps = new LocalGuestAiProperties();
        ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(aiProps);
        AiRecommendationMetrics metrics = new AiRecommendationMetrics(registry);
        ScoreCalculator scoreCalculator = new ScoreCalculator(
                new RegionMatchPolicy(adjacent, scoring),
                new StyleMatchPolicy(scoring),
                new BudgetMatchPolicy(scoring),
                new ActivityMatchPolicy(scoring),
                new LanguageMatchPolicy(scoring),
                new FeedbackMatchPolicy(scoring),
                new ComboMatchPolicy(scoring),
                metrics
        );
        return new MatchingEngine(
                scoreCalculator,
                new ReasonGenerator(adjacent, scoring),
                adjacent,
                DiversityRerankSnapshot.defaults(),
                metrics
        );
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

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GuideRecommendResponse response = engine(registry).recommend(pref, guides, 2);

        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().get(0).getGuideId()).isEqualTo(3L);
        assertThat(response.getRecommendations().get(0).getScore())
                .isGreaterThanOrEqualTo(response.getRecommendations().get(1).getScore());
        assertThat(registry.summary("localguest.ai.recommend.diversity_penalty_magnitude",
                        "policy_version", AiRecommendationTuning.POLICY_VERSION).count())
                .isEqualTo(1);
    }

    @Test
    void recommend_should_not_pick_same_guide_id_twice_when_pool_contains_duplicates() {
        TravelerPreference pref = TravelerPreference.builder()
                .region("제주")
                .travelStyle("감성")
                .budgetLevel("중간")
                .activityTags(List.of("카페"))
                .build();

        GuideAiProfile g1 = GuideAiProfile.builder()
                .guideId(1L)
                .guideName("A")
                .region("제주")
                .guideStyle("감성")
                .priceLevel("중간")
                .specialtyTags(List.of("카페"))
                .languages(List.of("한국어"))
                .build();
        GuideAiProfile g1dup = GuideAiProfile.builder()
                .guideId(1L)
                .guideName("A중복행")
                .region("제주")
                .guideStyle("감성")
                .priceLevel("중간")
                .specialtyTags(List.of("카페"))
                .languages(List.of("한국어"))
                .build();
        GuideAiProfile g2 = GuideAiProfile.builder()
                .guideId(2L)
                .guideName("B")
                .region("제주")
                .guideStyle("로컬")
                .priceLevel("중간")
                .specialtyTags(List.of("맛집"))
                .languages(List.of("한국어"))
                .build();

        GuideRecommendResponse response = engine(new SimpleMeterRegistry()).recommend(pref, List.of(g1, g1dup, g2), 3);

        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().stream().map(GuideRecommendItem::getGuideId).distinct().count())
                .isEqualTo(2L);
    }
}
