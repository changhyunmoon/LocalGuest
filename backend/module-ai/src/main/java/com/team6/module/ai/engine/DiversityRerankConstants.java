package com.team6.module.ai.engine;

/**
 * Diversity rerank(유사도 패널티)에 쓰이는 가중치. {@link MatchingEngine} 전용.
 */
public final class DiversityRerankConstants {

    private DiversityRerankConstants() {
    }

    public static final double DIVERSITY_LAMBDA = 15.0;
    public static final double REGION_SIM_WEIGHT = 1.0;
    public static final double STYLE_SIM_WEIGHT = 0.8;
    public static final double TAG_SIM_WEIGHT = 0.8;
}
