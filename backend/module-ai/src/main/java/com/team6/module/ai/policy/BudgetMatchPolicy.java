package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.springframework.stereotype.Component;

@Component
public class BudgetMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getBudgetLevel() == null || guide.getPriceLevel() == null) {
            return 0;
        }
        return pref.getBudgetLevel().equalsIgnoreCase(guide.getPriceLevel()) ? 15 : 0;
    }
}