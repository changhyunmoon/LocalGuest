package com.team6.module.ai.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 런타임에 고정된 스코어링 스냅샷(테스트·빈에서 주입).
 */
public record ScoringPolicySnapshot(
        int weightRegion,
        int weightRegionAdjacent,
        int weightStyle,
        int weightBudget,
        int weightBudgetAdjacent,
        int weightActivity,
        int weightLanguage,
        int softActivityPenaltyPerTag,
        int requiredActivityMissPenaltyPerTag,
        int niceToHaveActivityWeightPercent,
        int availabilityBoostPerDay,
        int availabilityBoostMax,
        int coldStartExplorationBonus,
        int coldStartMaxMatchRequestsWithoutReviews,
        int coldStartMaxChatStartsForExploration,
        int feedbackRefundPenaltyPerApproved,
        int feedbackRefundPenaltyMax,
        int feedbackLowRatingMinReviews,
        double feedbackLowRatingThreshold,
        int feedbackLowRatingPenalty,
        double feedbackVeryLowRatingThreshold,
        int feedbackVeryLowRatingPenalty,
        int feedbackMatchRequestBonusPerCount,
        int feedbackMatchRequestBonusMax,
        int feedbackProgressMatchBonusPerCount,
        int feedbackProgressMatchBonusMax,
        int feedbackChatStartBonusPerCount,
        int feedbackChatStartBonusMax,
        int lowSignalScoreThreshold,
        int fallbackMinImprovementOverBase,
        List<ComboRule> comboRules
) {
    public record ComboRule(
            String budgetLevel,
            String travelStyle,
            List<String> requireActivityTagsAll,
            int bonusPoints
    ) {
    }

    public static ScoringPolicySnapshot from(ScoringPolicySettings s) {
        if (s == null) {
            return defaults();
        }
        List<ComboRule> rules = new ArrayList<>();
        if (s.getComboRules() != null) {
            for (ScoringPolicySettings.ComboRuleSetting cr : s.getComboRules()) {
                if (cr == null) {
                    continue;
                }
                List<String> tags = cr.getRequireActivityTagsAll() == null
                        ? List.of()
                        : List.copyOf(cr.getRequireActivityTagsAll());
                rules.add(new ComboRule(
                        cr.getBudgetLevel(),
                        cr.getTravelStyle(),
                        tags,
                        cr.getBonusPoints()
                ));
            }
        }
        return new ScoringPolicySnapshot(
                s.getWeightRegion(),
                s.getWeightRegionAdjacent(),
                s.getWeightStyle(),
                s.getWeightBudget(),
                s.getWeightBudgetAdjacent(),
                s.getWeightActivity(),
                s.getWeightLanguage(),
                s.getSoftActivityPenaltyPerTag(),
                s.getRequiredActivityMissPenaltyPerTag(),
                s.getNiceToHaveActivityWeightPercent(),
                s.getAvailabilityBoostPerDay(),
                s.getAvailabilityBoostMax(),
                s.getColdStartExplorationBonus(),
                s.getColdStartMaxMatchRequestsWithoutReviews(),
                s.getColdStartMaxChatStartsForExploration(),
                s.getFeedbackRefundPenaltyPerApproved(),
                s.getFeedbackRefundPenaltyMax(),
                s.getFeedbackLowRatingMinReviews(),
                s.getFeedbackLowRatingThreshold(),
                s.getFeedbackLowRatingPenalty(),
                s.getFeedbackVeryLowRatingThreshold(),
                s.getFeedbackVeryLowRatingPenalty(),
                s.getFeedbackMatchRequestBonusPerCount(),
                s.getFeedbackMatchRequestBonusMax(),
                s.getFeedbackProgressMatchBonusPerCount(),
                s.getFeedbackProgressMatchBonusMax(),
                s.getFeedbackChatStartBonusPerCount(),
                s.getFeedbackChatStartBonusMax(),
                s.getLowSignalScoreThreshold(),
                s.getFallbackMinImprovementOverBase(),
                List.copyOf(rules)
        );
    }

    /**
     * {@link ScoringPolicySettings} 필드 기본값과 동일한 스냅샷(단위 테스트용).
     */
    public static ScoringPolicySnapshot defaults() {
        return from(new ScoringPolicySettings());
    }
}
