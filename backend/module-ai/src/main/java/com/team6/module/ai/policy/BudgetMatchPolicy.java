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
        // 1) 범위 기반(있으면 우선): 후보가 실제 금액 범위를 제공할 때만 적용된다.
        Integer prefMin = pref.getBudgetMinWon();
        Integer prefMax = pref.getBudgetMaxWon();
        Integer guideMin = guide.getPriceMinWon();
        Integer guideMax = guide.getPriceMaxWon();
        if (prefMin != null && prefMax != null && guideMin != null && guideMax != null) {
            boolean overlap = Math.max(prefMin, guideMin) <= Math.min(prefMax, guideMax);
            if (overlap) {
                return scoring.weightBudget();
            }
            boolean strict = Boolean.TRUE.equals(pref.getStrictBudget());
            return strict ? -scoring.strictBudgetMissPenalty() : 0;
        }

        // 2) 기존 tier 기반(후보가 금액 범위를 제공하지 않는 경우 등).
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
