package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.AiRecommendationTuning;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 리뷰·환불 등 **사후 피드백** 기반 감점. 선호(TravelerPreference)와 무관하게 가이드 신호만 본다.
 */
@Component
public class FeedbackMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        int penalty = 0;

        Integer refunds = guide.getApprovedRefundCount();
        if (refunds != null && refunds > 0) {
            penalty += Math.min(
                    refunds * AiRecommendationTuning.FEEDBACK_REFUND_PENALTY_PER_APPROVED,
                    AiRecommendationTuning.FEEDBACK_REFUND_PENALTY_MAX
            );
        }

        BigDecimal avg = guide.getAverageRating();
        Integer reviewCount = guide.getReviewCount();
        if (avg != null && reviewCount != null
                && reviewCount >= AiRecommendationTuning.FEEDBACK_LOW_RATING_MIN_REVIEWS) {
            double a = avg.doubleValue();
            if (a < AiRecommendationTuning.FEEDBACK_VERY_LOW_RATING_THRESHOLD) {
                penalty += AiRecommendationTuning.FEEDBACK_VERY_LOW_RATING_PENALTY;
            } else if (a < AiRecommendationTuning.FEEDBACK_LOW_RATING_THRESHOLD) {
                penalty += AiRecommendationTuning.FEEDBACK_LOW_RATING_PENALTY;
            }
        }

        return -penalty;
    }
}
