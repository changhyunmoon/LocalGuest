package com.team6.module.ai.engine;

/**
 * Diversity rerank 기본 상수(참고용). 런타임 튜닝은
 * {@code com.team6.module.ai.config.DiversityRerankSnapshot}·{@code localguest.ai.diversity-rerank}를 사용한다.
 *
 * @deprecated 새 코드는 {@code DiversityRerankSnapshot#defaults()}를 참고한다.
 */
@Deprecated
public final class DiversityRerankConstants {

    private DiversityRerankConstants() {
    }

    /** 유사도 패널티 강도. 클수록 Top-N에서 중복 성향 후보를 덜 고른다. */
    public static final double DIVERSITY_LAMBDA = 15.0;
    public static final double REGION_SIM_WEIGHT = 1.0;
    /**
     * 두 가이드 활동 지역이 다르지만, 모두 여행자 희망 지역 또는 그 인접 지역 안에 있을 때 지역 유사도(0~1).
     * {@link MatchingEngine}에서 {@link #REGION_SIM_WEIGHT}에 곱해 합산한다.
     */
    public static final double REGION_CLUSTER_SIMILARITY_RATIO = 0.42;
    public static final double STYLE_SIM_WEIGHT = 0.8;
    /** 전문 태그 Jaccard에 곱함. */
    public static final double TAG_SIM_WEIGHT = 0.8;
    /** 가능 언어 목록 Jaccard에 곱함. */
    public static final double LANGUAGE_SIM_WEIGHT = 0.55;
    /** 두 후보의 가격 티어(낮음/중간/높음)가 같을 때 가산. */
    public static final double PRICE_TIER_SIM_WEIGHT = 0.45;

    /**
     * 가격 티어가 인접(한 단계 차이)일 때 {@link #PRICE_TIER_SIM_WEIGHT}에 곱하는 유사도(0~1).
     */
    public static final double DEFAULT_PRICE_ADJACENT_SIMILARITY_RATIO = 0.38d;

    /**
     * 전문 태그 Jaccard가 이 값 이상이면 유사 코스로 보고 유효 Jaccard를 추가로 올린다.
     */
    public static final double DEFAULT_TAG_NEAR_DUP_THRESHOLD = 0.72d;

    /**
     * {@link #DEFAULT_TAG_NEAR_DUP_THRESHOLD} 이상 구간에서 Jaccard에 가산하는 부스트(상한 1.0).
     */
    public static final double DEFAULT_TAG_NEAR_DUP_BOOST = 0.28d;
}
