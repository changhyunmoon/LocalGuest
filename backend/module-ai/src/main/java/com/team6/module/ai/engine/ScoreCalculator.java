package com.team6.module.ai.engine;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.ComboMatchPolicy;
import com.team6.module.ai.policy.FeedbackMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    private final RegionMatchPolicy regionMatchPolicy;
    private final StyleMatchPolicy styleMatchPolicy;
    private final BudgetMatchPolicy budgetMatchPolicy;
    private final ActivityMatchPolicy activityMatchPolicy;
    private final LanguageMatchPolicy languageMatchPolicy;
    private final FeedbackMatchPolicy feedbackMatchPolicy;
    private final ComboMatchPolicy comboMatchPolicy;
    private final AiRecommendationMetrics recommendationMetrics;

    public int calculate(TravelerPreference pref, GuideAiProfile guide) {
        int score = 0;
        score += regionMatchPolicy.score(pref, guide);
        score += styleMatchPolicy.score(pref, guide);

        // 범위 예산 매칭 분기는 운영 관측에 남긴다(정책 점수 의미는 BudgetMatchPolicy에 위임).
        boolean rangeComparable =
                pref.getBudgetMinWon() != null && pref.getBudgetMaxWon() != null
                        && guide.getPriceMinWon() != null && guide.getPriceMaxWon() != null;
        if (rangeComparable) {
            boolean overlap = Math.max(pref.getBudgetMinWon(), guide.getPriceMinWon())
                    <= Math.min(pref.getBudgetMaxWon(), guide.getPriceMaxWon());
            recommendationMetrics.recordBudgetRangeMatch(overlap, AiRecommendationTuning.POLICY_VERSION);
        }

        score += budgetMatchPolicy.score(pref, guide);
        score += activityMatchPolicy.score(pref, guide);
        score += languageMatchPolicy.score(pref, guide);
        int feedback = feedbackMatchPolicy.score(pref, guide);
        score += feedback;
        score += comboMatchPolicy.score(pref, guide);
        if (feedback < 0) {
            recommendationMetrics.recordFeedbackPenalty(-feedback, AiRecommendationTuning.POLICY_VERSION);
        }
        return score;
    }
}
