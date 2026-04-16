package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySettings;
import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackMatchPolicyTest {

    private final ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
    private final FeedbackMatchPolicy policy = new FeedbackMatchPolicy(scoring);

    @Test
    void score_should_add_bonus_for_behavior_signals() {
        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(1L)
                .guideName("활성 가이드")
                .region("제주")
                .guideStyle("감성")
                .priceLevel("중간")
                .specialtyTags(List.of("카페"))
                .languages(List.of("한국어"))
                .matchRequestCount(4)
                .progressedMatchCount(2)
                .chatStartCount(3)
                .build();

        int score = policy.score(null, guide);

        assertThat(score).isEqualTo(16);
    }

    @Test
    void score_should_offset_bonus_with_penalties() {
        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(2L)
                .guideName("주의 가이드")
                .region("부산")
                .approvedRefundCount(2)
                .averageRating(BigDecimal.valueOf(3.2))
                .reviewCount(5)
                .matchRequestCount(3)
                .progressedMatchCount(1)
                .chatStartCount(1)
                .build();

        int score = policy.score(null, guide);

        assertThat(score).isEqualTo(-18);
    }

    @Test
    void score_should_add_cold_start_exploration_bonus_when_no_signals() {
        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(99L)
                .guideName("신규")
                .region("제주")
                .reviewCount(0)
                .approvedRefundCount(0)
                .matchRequestCount(0)
                .progressedMatchCount(0)
                .chatStartCount(0)
                .build();

        int score = policy.score(null, guide);

        assertThat(score).isEqualTo(scoring.coldStartExplorationBonus());
    }

    @Test
    void score_should_keep_cold_start_bonus_when_limited_match_requests_but_no_reviews() {
        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(100L)
                .guideName("초기노출")
                .region("제주")
                .reviewCount(0)
                .approvedRefundCount(0)
                .matchRequestCount(1)
                .progressedMatchCount(0)
                .chatStartCount(0)
                .build();

        int score = policy.score(null, guide);

        int expectedMr = scoring.feedbackMatchRequestBonusPerCount();
        assertThat(score).isEqualTo(expectedMr + scoring.coldStartExplorationBonus());
    }

    @Test
    void score_should_drop_cold_start_bonus_when_match_requests_exceed_threshold_without_reviews() {
        ScoringPolicySettings settings = new ScoringPolicySettings();
        settings.setColdStartMaxMatchRequestsWithoutReviews(0);
        ScoringPolicySnapshot tight = ScoringPolicySnapshot.from(settings);
        FeedbackMatchPolicy tightPolicy = new FeedbackMatchPolicy(tight);

        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(101L)
                .reviewCount(0)
                .matchRequestCount(1)
                .progressedMatchCount(0)
                .chatStartCount(0)
                .build();

        int score = tightPolicy.score(null, guide);
        assertThat(score).isEqualTo(tight.feedbackMatchRequestBonusPerCount());
    }

    @Test
    void score_should_add_bonus_for_click_signals() {
        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(3L)
                .guideName("클릭 가이드")
                .reviewCount(1)
                .recommendClickCount(12)
                .build();

        int score = policy.score(null, guide);

        assertThat(score).isEqualTo(scoring.recommendClickBonusMax());
    }
}
