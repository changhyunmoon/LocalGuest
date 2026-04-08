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

    public static final int DEFAULT_TOP_N = 3;

    /** 이 점수 미만이면 기존 조건 완화(fallback) 재시도를 시도한다. */
    public static final int LOW_SIGNAL_SCORE_THRESHOLD = 15;

    /**
     * 요청 지역과 정확히 일치하는 가이드 수가 이 값 이상이면, 해당 지역 가이드만으로 후보를 제한한다.
     * 미만이면 {@link AdjacentRegionMap} 기반으로 인접 지역까지 후보를 넓힌다.
     */
    public static final int MIN_EXACT_REGION_CANDIDATES = 2;
}
