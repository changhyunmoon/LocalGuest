package com.team6.module.ai.support;

import com.team6.module.ai.dto.response.GuideRecommendItem;

/**
 * {@link GuideRecommendItem.ReasonFact#getEvidenceSlot()}에 쓰이는 고정 키.
 * 프론트는 이 값으로 {@link GuideRecommendItem.MatchedEvidence} 필드·배지에 바로 매핑할 수 있다.
 * <ul>
 *   <li>{@link #REGION} → {@code matched.region}</li>
 *   <li>{@link #REGION_ADJACENT} → {@code matched.regionAdjacent}</li>
 *   <li>{@link #ACTIVITY_TAGS} → {@code matched.tags} (교집합, {@code values}는 표시용 부분집합과 동일 정규화)</li>
 *   <li>{@link #SOFT_PENALTY_TAGS} → {@code matched.softPenaltyOverlapTags}</li>
 *   <li>{@link #STYLE} → {@code matched.style}</li>
 *   <li>{@link #LANGUAGE} → {@code matched.languages}</li>
 *   <li>{@link #BUDGET} → {@code matched.budget}</li>
 *   <li>{@link #BUDGET_ADJACENT} → {@code matched.budgetAdjacent}</li>
 *   <li>{@link #FEEDBACK_REFUND}·{@link #FEEDBACK_RATING} → {@code matched}에 대응 불리언 없음,
 *       툴팁은 {@code reasonFacts.values} 사용</li>
 *   <li>{@link #GENERAL} → 종합 매칭, {@code matched}는 참고용</li>
 * </ul>
 */
public final class RecommendReasonEvidenceSlots {

    public static final String REGION = "REGION";
    public static final String REGION_ADJACENT = "REGION_ADJACENT";
    public static final String ACTIVITY_TAGS = "ACTIVITY_TAGS";
    public static final String SOFT_PENALTY_TAGS = "SOFT_PENALTY_TAGS";
    public static final String STYLE = "STYLE";
    public static final String LANGUAGE = "LANGUAGE";
    public static final String BUDGET = "BUDGET";
    public static final String BUDGET_ADJACENT = "BUDGET_ADJACENT";
    public static final String FEEDBACK_REFUND = "FEEDBACK_REFUND";
    public static final String FEEDBACK_RATING = "FEEDBACK_RATING";
    public static final String GENERAL = "GENERAL";

    private RecommendReasonEvidenceSlots() {
    }
}
