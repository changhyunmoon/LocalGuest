package com.team6.module.ai.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 룰 기반 스코어·피드백 가중치(YAML {@code localguest.ai.scoring-policy}).
 * 기본값은 기존 {@link com.team6.module.ai.policy.ScoreWeight}·{@link com.team6.module.ai.support.AiRecommendationTuning} 상수와 동일하다.
 * <p>
 * 운영 시 {@link LocalGuestAiProperties}의 후보 풀·다양성 블록과 같은 배포 단위로 맞추고,
 * 랭킹 의미 변경 시 {@link com.team6.module.ai.support.AiRecommendationTuning#POLICY_VERSION} 상향을 검토한다.
 */
@Getter
@Setter
public class ScoringPolicySettings {

    private int weightRegion = 30;
    private int weightRegionAdjacent = 15;
    private int weightStyle = 25;
    private int weightBudget = 15;
    private int weightBudgetAdjacent = 7;
    private int weightActivity = 10;
    private int weightLanguage = 10;

    private int softActivityPenaltyPerTag = 6;
    /**
     * {@link #requiredActivityTags}에 넣은 태그가 가이드 전문에 없을 때 태그당 감점.
     */
    private int requiredActivityMissPenaltyPerTag = 12;
    /**
     * nice-to-have 활동 매칭 시 {@link #weightActivity}에 곱할 퍼센트(50이면 절반).
     */
    private int niceToHaveActivityWeightPercent = 50;
    /**
     * 희망 기간 내 예약 가능한 날짜 1일당 랭킹 가산(상한은 {@link #availabilityBoostMax}).
     */
    private int availabilityBoostPerDay = 1;
    private int availabilityBoostMax = 8;

    private int coldStartExplorationBonus = 6;
    /**
     * 리뷰가 아직 없을 때, 이 값 이하의 매칭 요청 수면 콜드스타트 탐색 후보로 본다(첫·초기 노출 완충).
     * 기존은 매칭 요청이 한 건이라도 있으면 탐색 보너스가 꺼져 신입이 한 번 요청받으면 바로 불리해졌다.
     */
    private int coldStartMaxMatchRequestsWithoutReviews = 2;
    /**
     * 리뷰 없이 허용하는 채팅 시작 횟수 상한(이하면 탐색 후보 유지).
     */
    private int coldStartMaxChatStartsForExploration = 1;

    /** requiredLanguages를 만족하지 못한 경우 감점(가이드 언어 미충족). */
    private int requiredLanguageMissPenalty = 30;
    /** nice-to-have 언어 매칭 시 weightLanguage에 곱할 퍼센트(50이면 절반). */
    private int niceToHaveLanguageWeightPercent = 50;

    /** strictBudget=true이고 예산이 맞지 않을 때 감점(미스). */
    private int strictBudgetMissPenalty = 20;

    private int feedbackRefundPenaltyPerApproved = 8;
    private int feedbackRefundPenaltyMax = 24;
    private int feedbackLowRatingMinReviews = 3;
    private double feedbackLowRatingThreshold = 3.5d;
    private int feedbackLowRatingPenalty = 10;
    private double feedbackVeryLowRatingThreshold = 2.5d;
    private int feedbackVeryLowRatingPenalty = 16;

    private int feedbackMatchRequestBonusPerCount = 1;
    private int feedbackMatchRequestBonusMax = 5;
    private int feedbackProgressMatchBonusPerCount = 3;
    private int feedbackProgressMatchBonusMax = 12;
    private int feedbackChatStartBonusPerCount = 2;
    private int feedbackChatStartBonusMax = 8;

    /**
     * 이 값 미만이면 전략적 폴백(조건 완화)을 시도한다.
     */
    private int lowSignalScoreThreshold = 15;

    /**
     * 완화 후 Top1이 여전히 {@link #lowSignalScoreThreshold} 미만이어도,
     * 베이스 대비 이만큼 이상 올랐으면 수용한다(0이면 비활성).
     */
    private int fallbackMinImprovementOverBase = 4;

    /**
     * 제품 룰: 예산·스타일·활동 태그 조합 보너스(예: 높음 예산 + 시장).
     */
    private List<ComboRuleSetting> comboRules = new ArrayList<>();

    @Getter
    @Setter
    public static class ComboRuleSetting {
        /** 비우면 예산 조건 없음 */
        private String budgetLevel;
        /** 비우면 스타일 조건 없음 */
        private String travelStyle;
        /** 선호 활동 태그(정규화 전 표기; 매칭 시 {@link com.team6.module.ai.parser.KeywordNormalizer} 적용) */
        private List<String> requireActivityTagsAll = new ArrayList<>();
        private int bonusPoints;
    }
}
