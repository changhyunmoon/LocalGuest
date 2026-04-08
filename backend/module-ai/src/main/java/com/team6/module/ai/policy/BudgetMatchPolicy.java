package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class BudgetMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        if (pref.getBudgetLevel() == null || guide.getPriceLevel() == null) {
            return 0;
        }
        String p = pref.getBudgetLevel().trim();
        String g = guide.getPriceLevel().trim();
        if (p.equalsIgnoreCase(g)) {
            return ScoreWeight.BUDGET;
        }
        return budgetTiersAdjacent(p, g) ? ScoreWeight.BUDGET_ADJACENT : 0;
    }

    /**
     * 낮음(0) — 중간(1) — 높음(2) 한 단계 차이만 인접으로 본다.
     */
    private static boolean budgetTiersAdjacent(String a, String b) {
        int ia = budgetTierIndex(a);
        int ib = budgetTierIndex(b);
        if (ia < 0 || ib < 0) {
            return false;
        }
        return Math.abs(ia - ib) == 1;
    }

    private static int budgetTierIndex(String level) {
        String n = level.toLowerCase(Locale.ROOT);
        if (n.equals("낮음")) {
            return 0;
        }
        if (n.equals("중간")) {
            return 1;
        }
        if (n.equals("높음")) {
            return 2;
        }
        return -1;
    }
}