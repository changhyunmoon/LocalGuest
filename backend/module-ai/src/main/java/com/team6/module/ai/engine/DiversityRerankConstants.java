package com.team6.module.ai.engine;

/**
 * Diversity rerank(유사도 패널티)에 쓰이는 가중치. {@link MatchingEngine} 전용.
 * <p>
 * 유사도는 대략 0~1로 정규화한 뒤 {@code DIVERSITY_LAMBDA}를 곱해 점수에서 뺀다.
 * 가중치 합({@link #REGION_SIM_WEIGHT}+…+{@link #PRICE_TIER_SIM_WEIGHT})으로 나누어 스케일을 맞춘다.
 */
public final class DiversityRerankConstants {

    private DiversityRerankConstants() {
    }

    /** 유사도 패널티 강도. 클수록 Top-N에서 중복 성향 후보를 덜 고른다. */
    public static final double DIVERSITY_LAMBDA = 15.0;
    public static final double REGION_SIM_WEIGHT = 1.0;
    public static final double STYLE_SIM_WEIGHT = 0.8;
    /** 전문 태그 Jaccard에 곱함. */
    public static final double TAG_SIM_WEIGHT = 0.8;
    /** 가능 언어 목록 Jaccard에 곱함. */
    public static final double LANGUAGE_SIM_WEIGHT = 0.55;
    /** 두 후보의 가격 티어(낮음/중간/높음)가 같을 때 가산. */
    public static final double PRICE_TIER_SIM_WEIGHT = 0.45;
}
