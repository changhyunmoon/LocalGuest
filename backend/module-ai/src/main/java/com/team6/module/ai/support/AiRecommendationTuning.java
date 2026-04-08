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
    public static final String POLICY_VERSION = "2026.04.3";

    public static final int DEFAULT_TOP_N = 3;

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
}
