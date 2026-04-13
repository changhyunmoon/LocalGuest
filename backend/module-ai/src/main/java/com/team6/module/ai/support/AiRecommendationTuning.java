package com.team6.module.ai.support;

/**
 * 룰 기반 추천의 기본 튜닝 값(yml 없이 코드로 고정). 변경 시 회귀 테스트를 함께 점검한다.
 * <p>
 * Diversity rerank 가중치는 {@link com.team6.module.ai.engine.DiversityRerankConstants},
 * 정책별 점수 가중은 {@link com.team6.module.ai.policy.ScoreWeight}를 참고한다.
 */
public final class AiRecommendationTuning {

    private AiRecommendationTuning() {
    }

    /**
     * 룰/스코어/Reason/응답 계약이 바뀔 때마다 올린다. 로그·API에서 동일 프롬프트 비교 시 정책 변경 여부를 구분하는 데 쓴다.
     */
    public static final String POLICY_VERSION = "2026.04.13";

    public static final int DEFAULT_TOP_N = 3;

    /** 추천 카드·응답에 실을 공개 피드 썸네일 URL 상한(최신순). */
    public static final int PUBLIC_FEED_THUMBNAIL_MAX = 4;

    /** 이 점수 미만이면 기존 조건 완화(fallback) 재시도를 시도한다. */
    public static final int LOW_SIGNAL_SCORE_THRESHOLD = 15;

    /**
     * 요청 지역과 정확히 일치하는 가이드 수가 이 값 이상이면, 해당 지역 가이드만으로 후보를 제한한다.
     * 미만이면 {@link AdjacentRegionMap} 기반으로 인접 지역까지 후보를 넓힌다.
     */
    public static final int MIN_EXACT_REGION_CANDIDATES = 2;

    /**
     * 가이드 전문 태그가 {@link com.team6.module.ai.model.TravelerPreference#getSoftPenaltyActivityTags()}
     * 와 겹칠 때마다 활동 점수에서 차감하는 값(한 태그당, {@link com.team6.module.ai.policy.ScoreWeight#ACTIVITY}와 동일 스케일).
     */
    public static final int SOFT_ACTIVITY_PENALTY_PER_TAG = 6;

    /** 승인 환불 1건당 감점(상한 {@link #FEEDBACK_REFUND_PENALTY_MAX}). */
    public static final int FEEDBACK_REFUND_PENALTY_PER_APPROVED = 8;
    public static final int FEEDBACK_REFUND_PENALTY_MAX = 24;

    /** 평균 평점 감점: 최소 리뷰 수(노이즈 완화). */
    public static final int FEEDBACK_LOW_RATING_MIN_REVIEWS = 3;
    public static final double FEEDBACK_LOW_RATING_THRESHOLD = 3.5;
    public static final int FEEDBACK_LOW_RATING_PENALTY = 10;
    public static final double FEEDBACK_VERY_LOW_RATING_THRESHOLD = 2.5;
    public static final int FEEDBACK_VERY_LOW_RATING_PENALTY = 16;

    /** 실제 행동 데이터 기반 보정(요청/진행/채팅)은 과적합을 피하기 위해 소폭 가산만 적용한다. */
    public static final int FEEDBACK_MATCH_REQUEST_BONUS_PER_COUNT = 1;
    public static final int FEEDBACK_MATCH_REQUEST_BONUS_MAX = 5;
    public static final int FEEDBACK_PROGRESS_MATCH_BONUS_PER_COUNT = 3;
    public static final int FEEDBACK_PROGRESS_MATCH_BONUS_MAX = 12;
    public static final int FEEDBACK_CHAT_START_BONUS_PER_COUNT = 2;
    public static final int FEEDBACK_CHAT_START_BONUS_MAX = 8;
}
