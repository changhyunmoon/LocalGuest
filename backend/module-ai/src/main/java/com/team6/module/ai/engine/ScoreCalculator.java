package com.team6.module.ai.engine;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
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
    private final AiRecommendationMetrics recommendationMetrics;

    public int calculate(TravelerPreference pref, GuideAiProfile guide) {
        int score = 0;
        score += regionMatchPolicy.score(pref, guide);
        score += styleMatchPolicy.score(pref, guide);
        score += budgetMatchPolicy.score(pref, guide);
        score += activityMatchPolicy.score(pref, guide);
        score += languageMatchPolicy.score(pref, guide);
        int feedback = feedbackMatchPolicy.score(pref, guide);
        score += feedback;
        if (feedback < 0) {
            recommendationMetrics.recordFeedbackPenalty(-feedback, AiRecommendationTuning.POLICY_VERSION);
        }
        return score;
    }
}