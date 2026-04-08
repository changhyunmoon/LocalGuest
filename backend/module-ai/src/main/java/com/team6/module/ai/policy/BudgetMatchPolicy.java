package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.BudgetTier;
import org.springframework.stereotype.Component;

@Component
public class BudgetMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getBudgetLevel() == null || guide.getPriceLevel() == null) {
            return 0;
        }
        String p = pref.getBudgetLevel().trim();
        String g = guide.getPriceLevel().trim();
        if (BudgetTier.exactMatch(p, g)) {
            return ScoreWeight.BUDGET;
        }
        return BudgetTier.adjacentTiers(p, g) ? ScoreWeight.BUDGET_ADJACENT : 0;
    }
}