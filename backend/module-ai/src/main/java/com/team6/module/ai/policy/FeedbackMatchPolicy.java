package com.team6.module.ai.policy;

import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 리뷰·환불 감점 + 실제 전환 행동 데이터 기반 소폭 가산 + 콜드스타트 탐색 보너스.
 * 선호(TravelerPreference)와 무관하게 가이드의 운영 품질/서비스 연결 신호만 본다.
 */
@Component
@RequiredArgsConstructor
public class FeedbackMatchPolicy {

    private final ScoringPolicySnapshot scoring;

    public int score(TravelerPreference pref, GuideAiProfile guide) {
        int penalty = 0;
        int bonus = 0;

        Integer refunds = guide.getApprovedRefundCount();
        if (refunds != null && refunds > 0) {
            penalty += Math.min(
                    refunds * scoring.feedbackRefundPenaltyPerApproved(),
                    scoring.feedbackRefundPenaltyMax()
            );
        }

        BigDecimal avg = guide.getAverageRating();
        Integer reviewCount = guide.getReviewCount();
        if (avg != null && reviewCount != null
                && reviewCount >= scoring.feedbackLowRatingMinReviews()) {
            double a = avg.doubleValue();
            if (a < scoring.feedbackVeryLowRatingThreshold()) {
                penalty += scoring.feedbackVeryLowRatingPenalty();
            } else if (a < scoring.feedbackLowRatingThreshold()) {
                penalty += scoring.feedbackLowRatingPenalty();
            }
        }

        Integer matchRequests = guide.getMatchRequestCount();
        if (matchRequests != null && matchRequests > 0) {
            bonus += Math.min(
                    matchRequests * scoring.feedbackMatchRequestBonusPerCount(),
                    scoring.feedbackMatchRequestBonusMax()
            );
        }

        Integer progressedMatches = guide.getProgressedMatchCount();
        if (progressedMatches != null && progressedMatches > 0) {
            bonus += Math.min(
                    progressedMatches * scoring.feedbackProgressMatchBonusPerCount(),
                    scoring.feedbackProgressMatchBonusMax()
            );
        }

        Integer chatStarts = guide.getChatStartCount();
        if (chatStarts != null && chatStarts > 0) {
            bonus += Math.min(
                    chatStarts * scoring.feedbackChatStartBonusPerCount(),
                    scoring.feedbackChatStartBonusMax()
            );
        }

        if (isColdStartExplorationCandidate(guide)) {
            bonus += scoring.coldStartExplorationBonus();
        }

        return bonus - penalty;
    }

    /**
     * 리뷰가 없고 운영 리스크 신호가 낮은 초기 단계(콜드스타트)로 본다.
     * <p>
     * 매칭 요청·채팅이 소량이어도 리뷰가 쌓이기 전에는 기존 인기 가이드 대비 밀리기 쉬워,
     * {@link com.team6.module.ai.config.ScoringPolicySnapshot#coldStartMaxMatchRequestsWithoutReviews()} 등으로
     * 탐색 보너스를 유지한다.
     */
    private boolean isColdStartExplorationCandidate(GuideAiProfile guide) {
        Integer rc = guide.getReviewCount();
        if (rc != null && rc > 0) {
            return false;
        }
        Integer refunds = guide.getApprovedRefundCount();
        if (refunds != null && refunds > 0) {
            return false;
        }
        Integer mr = guide.getMatchRequestCount();
        int maxMr = scoring.coldStartMaxMatchRequestsWithoutReviews();
        if (mr != null && mr > maxMr) {
            return false;
        }
        Integer pm = guide.getProgressedMatchCount();
        if (pm != null && pm > 0) {
            return false;
        }
        Integer cs = guide.getChatStartCount();
        int maxChat = scoring.coldStartMaxChatStartsForExploration();
        return cs == null || cs <= maxChat;
    }
}
