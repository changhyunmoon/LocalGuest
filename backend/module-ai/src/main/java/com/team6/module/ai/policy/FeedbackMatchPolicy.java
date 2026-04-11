package com.team6.module.ai.policy;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.AiRecommendationTuning;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 리뷰·환불 감점 + 실제 전환 행동 데이터 기반 소폭 가산.
 * 선호(TravelerPreference)와 무관하게 가이드의 운영 품질/서비스 연결 신호만 본다.
 */
@Component
public class FeedbackMatchPolicy {

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        int penalty = 0;
        int bonus = 0;

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

        Integer matchRequests = guide.getMatchRequestCount();
        if (matchRequests != null && matchRequests > 0) {
            bonus += Math.min(
                    matchRequests * AiRecommendationTuning.FEEDBACK_MATCH_REQUEST_BONUS_PER_COUNT,
                    AiRecommendationTuning.FEEDBACK_MATCH_REQUEST_BONUS_MAX
            );
        }

        Integer progressedMatches = guide.getProgressedMatchCount();
        if (progressedMatches != null && progressedMatches > 0) {
            bonus += Math.min(
                    progressedMatches * AiRecommendationTuning.FEEDBACK_PROGRESS_MATCH_BONUS_PER_COUNT,
                    AiRecommendationTuning.FEEDBACK_PROGRESS_MATCH_BONUS_MAX
            );
        }

        Integer chatStarts = guide.getChatStartCount();
        if (chatStarts != null && chatStarts > 0) {
            bonus += Math.min(
                    chatStarts * AiRecommendationTuning.FEEDBACK_CHAT_START_BONUS_PER_COUNT,
                    AiRecommendationTuning.FEEDBACK_CHAT_START_BONUS_MAX
            );
        }

        return bonus - penalty;
    }
}
