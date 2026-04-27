package com.team6.module.ai.support;

import java.util.Locale;

/**
 * 예산 티어(낮음/중간/높음) 비교. {@link com.team6.module.ai.policy.BudgetMatchPolicy},
 * {@link com.team6.module.ai.engine.ReasonGenerator}, {@link com.team6.module.ai.engine.MatchingEngine}에서 공통 사용.
 */
public final class BudgetTier {

    private BudgetTier() {
    }

    public static boolean exactMatch(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    public static boolean adjacentTiers(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        int ia = tierIndex(a.trim());
        int ib = tierIndex(b.trim());
        if (ia < 0 || ib < 0) {
            return false;
        }
        return Math.abs(ia - ib) == 1;
    }

    public static int tierIndex(String level) {
        String n = level.toLowerCase(Locale.ROOT);
        return switch (n) {
            case "낮음" -> 0;
            case "중간" -> 1;
            case "높음" -> 2;
            default -> -1;
        };
    }
}
