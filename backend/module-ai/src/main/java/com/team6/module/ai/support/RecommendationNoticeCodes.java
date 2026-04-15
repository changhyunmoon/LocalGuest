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
    /** 지역은 있으나 예산·일정 등 신호가 적을 때(프론트: 추가 질문·낮은 신뢰 UI) */
    public static final String PROMPT_PARSE_CONFIDENCE_LOW = "PROMPT_PARSE_CONFIDENCE_LOW";
    /** 금액·티어는 못 잡았는데 예산 관련 표현이 있는 경우 */
    public static final String PROMPT_BUDGET_AMBIGUOUS = "PROMPT_BUDGET_AMBIGUOUS";
    /** 기간·일정 관련 표현이 있는데 일수로 확정하지 못한 경우 */
    public static final String PROMPT_DURATION_AMBIGUOUS = "PROMPT_DURATION_AMBIGUOUS";
    /**
     * 선호 활동과 제외 태그가 겹쳐 제외를 우선하거나, 제외 목록을 ‘꼭/위주’ 표현으로 완화한 경우.
     */
    public static final String PROMPT_PREFERENCE_CONFLICT_RESOLVED = "PROMPT_PREFERENCE_CONFLICT_RESOLVED";
    public static final String FALLBACK_RELAXED_NO_MATCH = "FALLBACK_RELAXED_NO_MATCH";
    public static final String FALLBACK_LOW_SCORE_RELAXED = "FALLBACK_LOW_SCORE_RELAXED";
    /** 전략적 완화: 활동 태그만 제거한 단계에서 수용 가능한 결과를 얻음 */
    public static final String FALLBACK_RELAXED_ACTIVITY_TAGS = "FALLBACK_RELAXED_ACTIVITY_TAGS";
    /** 전략적 완화: 여행 스타일 제거(누적 단계 중 해당 스텝)로 수용 가능 */
    public static final String FALLBACK_RELAXED_TRAVEL_STYLE = "FALLBACK_RELAXED_TRAVEL_STYLE";
    /** 전략적 완화: 지역 제거(누적 단계 중 해당 스텝)로 수용 가능 */
    public static final String FALLBACK_RELAXED_REGION = "FALLBACK_RELAXED_REGION";
    /** 전략적 완화 체인을 모두 시도했으나 임계 이상으로 개선되지 않음 */
    public static final String FALLBACK_STRATEGIC_EXHAUSTED = "FALLBACK_STRATEGIC_EXHAUSTED";
}
