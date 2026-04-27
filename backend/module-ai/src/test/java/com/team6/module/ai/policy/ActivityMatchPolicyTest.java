package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityMatchPolicyTest {

    private final ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
    private final ActivityMatchPolicy policy = new ActivityMatchPolicy(scoring);

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
        assertThat(score).isEqualTo(scoring.weightActivity() - scoring.softActivityPenaltyPerTag());
    }

    @Test
    void score_should_apply_penalty_when_required_tag_missing_on_guide() {
        TravelerPreference pref = TravelerPreference.builder()
                .activityTags(List.of("바다", "카페"))
                .requiredActivityTags(List.of("바다"))
                .build();

        GuideAiProfile guide = GuideAiProfile.builder()
                .guideId(1L)
                .specialtyTags(List.of("카페"))
                .build();

        int score = policy.score(pref, guide);
        // 카페만 겹치고(일반 가중), '꼭' 바다는 없음 → 필수 미스 감점으로 상쇄
        int expected = Math.max(0, scoring.weightActivity() - scoring.requiredActivityMissPenaltyPerTag());
        assertThat(score).isEqualTo(expected);
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
