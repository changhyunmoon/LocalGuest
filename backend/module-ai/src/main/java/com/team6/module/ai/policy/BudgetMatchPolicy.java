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
        boolean adjacent = BudgetTier.adjacentTiers(p, g);
        if (adjacent) {
            return scoring.weightBudgetAdjacent();
        }
        boolean strict = Boolean.TRUE.equals(pref.getStrictBudget());
        return strict ? -scoring.strictBudgetMissPenalty() : 0;
    }
}
