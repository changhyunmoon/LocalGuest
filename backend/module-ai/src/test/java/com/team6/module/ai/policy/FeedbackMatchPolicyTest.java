package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackMatchPolicyTest {

    private final FeedbackMatchPolicy policy = new FeedbackMatchPolicy();

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
}
