package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackMatchPolicyTest {

    private final FeedbackMatchPolicy policy = new FeedbackMatchPolicy();

    @Test
    void no_signal_returns_zero() {
        TravelerPreference pref = TravelerPreference.builder().region("제주").activityTags(List.of()).build();
        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(1L)
                .guideName("a")
                .region("제주")
                .languages(List.of())
                .specialtyTags(List.of())
                .build();
        assertThat(policy.score(pref, guide)).isZero();
    }

    @Test
    void approved_refunds_apply_penalty_capped() {
        TravelerPreference pref = TravelerPreference.builder().region("제주").activityTags(List.of()).build();
        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(1L)
                .guideName("a")
                .region("제주")
                .languages(List.of())
                .specialtyTags(List.of())
                .approvedRefundCount(5)
                .build();
        assertThat(policy.score(pref, guide)).isEqualTo(-24);
    }

    @Test
    void low_average_rating_penalty() {
        TravelerPreference pref = TravelerPreference.builder().region("제주").activityTags(List.of()).build();
        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(1L)
                .guideName("a")
                .region("제주")
                .languages(List.of())
                .specialtyTags(List.of())
                .averageRating(new BigDecimal("2.4"))
                .reviewCount(5)
                .build();
        assertThat(policy.score(pref, guide)).isEqualTo(-16);
    }
}
