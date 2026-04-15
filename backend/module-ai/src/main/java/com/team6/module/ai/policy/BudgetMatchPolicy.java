package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.BudgetTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BudgetMatchPolicy {

    private final ScoringPolicySnapshot scoring;

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getBudgetLevel() == null || guide.getPriceLevel() == null) {
            return 0;
        }
        String p = pref.getBudgetLevel().trim();
        String g = guide.getPriceLevel().trim();
        if (BudgetTier.exactMatch(p, g)) {
            return scoring.weightBudget();
        }
        return BudgetTier.adjacentTiers(p, g) ? scoring.weightBudgetAdjacent() : 0;
    }
}
