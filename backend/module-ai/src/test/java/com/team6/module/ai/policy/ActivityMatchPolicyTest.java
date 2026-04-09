package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityMatchPolicyTest {

    private final ActivityMatchPolicy policy = new ActivityMatchPolicy();

    @Test
    void score_should_reduce_when_guide_matches_soft_penalty_tag() {
        TravelerPreference pref = TravelerPreference.builder()
                .activityTags(List.of("카페"))
                .softPenaltyActivityTags(List.of("등산"))
                .build();

        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(1L)
                .specialtyTags(List.of("카페", "등산"))
                .build();

        int score = policy.score(pref, guide);
        // 카페 매칭 10, 등산 soft 패널티 -6 -> 4
        assertThat(score).isEqualTo(4);
    }

    @Test
    void score_should_be_zero_when_only_soft_penalty_hits() {
        TravelerPreference pref = TravelerPreference.builder()
                .activityTags(List.of())
                .softPenaltyActivityTags(List.of("등산"))
                .build();

        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(1L)
                .specialtyTags(List.of("등산"))
                .build();

        assertThat(policy.score(pref, guide)).isEqualTo(0);
    }
}
