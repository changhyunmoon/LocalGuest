package com.team6.module.ai.engine;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.FeedbackMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCalculatorFeedbackMetricsTest {

    @Test
    void calculate_records_feedback_penalty_metrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRecommendationMetrics metrics = new AiRecommendationMetrics(registry);
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(new LocalGuestAiProperties());
        ScoreCalculator calculator = new ScoreCalculator(
                new RegionMatchPolicy(adjacent),
                new StyleMatchPolicy(),
                new BudgetMatchPolicy(),
                new ActivityMatchPolicy(),
                new LanguageMatchPolicy(),
                new FeedbackMatchPolicy(),
                metrics
        );

        TravelerPreference pref = TravelerPreference.builder()
                .region("제주")
                .activityTags(List.of())
                .build();
        GuideAiProfile penalized = GuideAiProfile.builder()
                .guideId(1L)
                .guideName("x")
                .region("제주")
                .languages(List.of())
                .specialtyTags(List.of())
                .approvedRefundCount(2)
                .build();
        calculator.calculate(pref, penalized);

        String pv = AiRecommendationTuning.POLICY_VERSION;
        assertThat(registry.counter("localguest.ai.recommend.feedback_penalty_hits", "policy_version", pv).count())
                .isEqualTo(1);
        assertThat(registry.summary("localguest.ai.recommend.feedback_penalty_magnitude", "policy_version", pv).count())
                .isEqualTo(1);

        GuideAiProfile clean = GuideAiProfile.builder()
                .guideId(2L)
                .guideName("y")
                .region("제주")
                .languages(List.of())
                .specialtyTags(List.of())
                .averageRating(new BigDecimal("5.0"))
                .reviewCount(10)
                .approvedRefundCount(0)
                .build();
        calculator.calculate(pref, clean);

        assertThat(registry.counter("localguest.ai.recommend.feedback_penalty_hits", "policy_version", pv).count())
                .isEqualTo(1);
    }
}
