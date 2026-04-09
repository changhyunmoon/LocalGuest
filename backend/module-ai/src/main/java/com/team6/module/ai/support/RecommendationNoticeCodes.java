package com.team6.module.ai.support;

/**
 * 프롬프트 추천 API {@code notice}에 대응하는 기계 판별 코드(프론트 i18n·토스트 매핑용).
 */
public final class RecommendationNoticeCodes {

    private RecommendationNoticeCodes() {
    }

    public static final String REGION_REQUIRED = "REGION_REQUIRED";
    public static final String ADJACENT_REGION_INCLUDED = "ADJACENT_REGION_INCLUDED";
    public static final String SPARSE_GUIDE_POOL = "SPARSE_GUIDE_POOL";
    public static final String NO_GUIDE_CANDIDATES = "NO_GUIDE_CANDIDATES";
    public static final String PROMPT_DETAIL_REQUESTED = "PROMPT_DETAIL_REQUESTED";
    public static final String FALLBACK_RELAXED_NO_MATCH = "FALLBACK_RELAXED_NO_MATCH";
    public static final String FALLBACK_LOW_SCORE_RELAXED = "FALLBACK_LOW_SCORE_RELAXED";
}
