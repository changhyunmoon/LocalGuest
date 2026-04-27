package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetMatchPolicyTest {

    private final ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
    private final BudgetMatchPolicy policy = new BudgetMatchPolicy(scoring);

    @Test
    void score_should_be_full_when_exact_tier_match() {
        int s = policy.score(
                TravelerPreference.builder().budgetLevel("중간").build(),
                GuideAiProfile.builder().priceLevel("중간").build()
        );
        assertThat(s).isEqualTo(scoring.weightBudget());
    }

    @Test
    void score_should_be_adjacent_when_one_tier_off() {
        int lowMid = policy.score(
                TravelerPreference.builder().budgetLevel("낮음").build(),
                GuideAiProfile.builder().priceLevel("중간").build()
        );
        int midHigh = policy.score(
                TravelerPreference.builder().budgetLevel("중간").build(),
                GuideAiProfile.builder().priceLevel("높음").build()
        );
        assertThat(lowMid).isEqualTo(scoring.weightBudgetAdjacent());
        assertThat(midHigh).isEqualTo(scoring.weightBudgetAdjacent());
    }

    @Test
    void score_should_be_zero_when_tiers_two_steps_apart() {
        int s = policy.score(
                TravelerPreference.builder().budgetLevel("낮음").build(),
                GuideAiProfile.builder().priceLevel("높음").build()
        );
        assertThat(s).isEqualTo(0);
    }

    @Test
    void score_should_be_zero_when_pref_or_guide_budget_missing() {
        assertThat(policy.score(
                TravelerPreference.builder().budgetLevel("중간").build(),
                GuideAiProfile.builder().priceLevel(null).build()
        )).isEqualTo(0);
        assertThat(policy.score(
                TravelerPreference.builder().budgetLevel(null).build(),
                GuideAiProfile.builder().priceLevel("중간").build()
        )).isEqualTo(0);
    }

    @Test
    void score_should_use_budget_range_when_available() {
        int ok = policy.score(
                TravelerPreference.builder().budgetMinWon(100_000).budgetMaxWon(200_000).strictBudget(true).build(),
                GuideAiProfile.builder().priceMinWon(150_000).priceMaxWon(250_000).build()
        );
        assertThat(ok).isEqualTo(scoring.weightBudget());

        int missStrict = policy.score(
                TravelerPreference.builder().budgetMinWon(100_000).budgetMaxWon(200_000).strictBudget(true).build(),
                GuideAiProfile.builder().priceMinWon(210_000).priceMaxWon(300_000).build()
        );
        assertThat(missStrict).isEqualTo(-scoring.strictBudgetMissPenalty());
    }
}
