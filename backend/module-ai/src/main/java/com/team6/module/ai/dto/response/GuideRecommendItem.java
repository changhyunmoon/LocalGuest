package com.team6.module.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "GuideRecommendItem", description = "단일 가이드 추천 결과")
@Getter
@Builder
public class GuideRecommendItem {
    private Long guideId;
    private String guideName;

    @Schema(description = "프로필 대표 이미지 URL")
    private String representativeImageUrl;

    @Schema(description = "활동 지역(가이드 프로필 기준)")
    private String region;

    @Schema(description = "가격대 티어(낮음/중간/높음 등, 후보 프로필에서 전달된 값)")
    private String priceLevel;

    @Schema(description = "평균 평점(미전달 시 null)")
    private BigDecimal averageRating;

    @Schema(description = "리뷰 수(미전달 시 null)")
    private Integer reviewCount;

    @Schema(description = "공개 피드 이미지 URL(최신순, 상한 적용)")
    private List<String> publicFeedThumbnailUrls;

    @Schema(description = "룰 기반 합산 점수")
    private int score;
    @Schema(description = "사람이 읽는 추천 이유(상위 근거 문구 연결)")
    private String reason;
    /**
     * 구조화된 추천 근거 코드(표시된 상위 근거와 동일 순서, {@link com.team6.module.ai.engine.ReasonGenerator} 상한).
     */
    @Schema(description = "구조화 근거 코드(ReasonGenerator 상한까지, 표시 순서와 동일)")
    private List<String> reasonCodes;
    /**
     * 근거 코드별 매칭 값(예: 태그/언어 목록).
     */
    @Schema(
            description = "reasonCodes와 동일 순서. evidenceSlot으로 matched(배지)·툴팁 매핑 — "
                    + "com.team6.module.ai.support.RecommendReasonEvidenceSlots 참고"
    )
    private List<ReasonFact> reasonFacts;

    @Schema(
            description = "매칭 증거(배지). reasonFacts[].evidenceSlot과 1:1로 대응(REGION→region 등)"
    )
    private MatchedEvidence matched;

    @Schema(name = "GuideRecommendReasonFact")
    @Getter
    @Builder
    public static class ReasonFact {
        @Schema(description = "ReasonGenerator 코드(예: REGION_MATCH, BUDGET_ADJACENT, FEEDBACK_VERY_LOW_RATING)")
        private String code;
        @Schema(
                description = "matched 배지 매핑 키. REGION/REGION_ADJACENT/ACTIVITY_TAGS/SOFT_PENALTY_TAGS/"
                        + "STYLE/LANGUAGE/BUDGET/BUDGET_ADJACENT/FEEDBACK_REFUND/FEEDBACK_RATING/GENERAL",
                allowableValues = {
                        "REGION", "REGION_ADJACENT", "ACTIVITY_TAGS", "SOFT_PENALTY_TAGS", "STYLE", "LANGUAGE",
                        "BUDGET", "BUDGET_ADJACENT", "FEEDBACK_REFUND", "FEEDBACK_RATING", "GENERAL"
                }
        )
        private String evidenceSlot;
        @Schema(description = "해당 근거에 대응하는 값 목록(태그·언어·평점 문자열 등)")
        private List<String> values;
    }

    @Schema(name = "GuideRecommendMatchedEvidence")
    @Getter
    @Builder
    public static class MatchedEvidence {
        @Schema(description = "reasonFacts evidenceSlot=REGION 과 함께 사용")
        private boolean region;
        @Schema(description = "reasonFacts evidenceSlot=REGION_ADJACENT. 희망 지역과 정확히 같지는 않지만 인접 지역")
        private boolean regionAdjacent;
        @Schema(description = "reasonFacts evidenceSlot=STYLE 과 함께 사용")
        private boolean style;
        @Schema(description = "reasonFacts evidenceSlot=BUDGET. 예산 티어(낮음/중간/높음) 완전 일치")
        private boolean budget;
        @Schema(description = "reasonFacts evidenceSlot=BUDGET_ADJACENT. budget이 true이면 보통 false")
        private boolean budgetAdjacent;
        @Schema(description = "reasonFacts evidenceSlot=ACTIVITY_TAGS. 선호 활동 태그∩가이드 전문 태그(정규화)")
        private List<String> tags;
        @Schema(description = "reasonFacts evidenceSlot=LANGUAGE. 선호 언어∩가이드 가능 언어")
        private List<String> languages;
        /**
         * 여행자 soft 부정 태그와 가이드 전문 태그 교집합(배지·툴팁용). 없으면 빈 목록.
         */
        @Schema(description = "reasonFacts evidenceSlot=SOFT_PENALTY_TAGS. soft 부정 태그∩가이드 전문 태그")
        private List<String> softPenaltyOverlapTags;
    }
}