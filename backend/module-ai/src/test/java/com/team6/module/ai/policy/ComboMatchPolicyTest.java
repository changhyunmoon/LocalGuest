package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySettings;
import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComboMatchPolicyTest {

    @Test
    void score_adds_bonus_when_budget_and_activity_tags_match_rule() {
        ScoringPolicySettings settings = new ScoringPolicySettings();
        ScoringPolicySettings.ComboRuleSetting rule = new ScoringPolicySettings.ComboRuleSetting();
        rule.setBudgetLevel("높음");
        rule.setRequireActivityTagsAll(List.of("시장"));
        rule.setBonusPoints(7);
        settings.getComboRules().add(rule);
        ScoringPolicySnapshot scoring = ScoringPolicySnapshot.from(settings);
        ComboMatchPolicy policy = new ComboMatchPolicy(scoring);

        TravelerPreference pref = TravelerPreference.builder()
                .budgetLevel("높음")
                .activityTags(List.of("시장", "맛집"))
                .build();
        GuideAiProfile guide = GuideAiProfile.builder().guideId(1L).region("서울").build();

        assertThat(policy.score(pref, guide)).isEqualTo(7);
    }

    @Test
    void score_zero_when_required_tag_missing() {
        ScoringPolicySettings settings = new ScoringPolicySettings();
        ScoringPolicySettings.ComboRuleSetting rule = new ScoringPolicySettings.ComboRuleSetting();
        rule.setBudgetLevel("높음");
        rule.setRequireActivityTagsAll(List.of("시장"));
        rule.setBonusPoints(7);
        settings.getComboRules().add(rule);
        ComboMatchPolicy policy = new ComboMatchPolicy(ScoringPolicySnapshot.from(settings));

        TravelerPreference pref = TravelerPreference.builder()
                .budgetLevel("높음")
                .activityTags(List.of("맛집"))
                .build();

        assertThat(policy.score(pref, GuideAiProfile.builder().guideId(1L).build())).isZero();
    }
}
