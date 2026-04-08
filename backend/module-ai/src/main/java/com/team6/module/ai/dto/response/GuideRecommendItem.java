package com.team6.module.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(name = "GuideRecommendItem", description = "단일 가이드 추천 결과")
@Getter
@Builder
public class GuideRecommendItem {
    private Long guideId;
    private String guideName;
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
    @Schema(description = "reasonCodes와 동일 순서의 근거별 값(태그·언어 등)")
    private List<ReasonFact> reasonFacts;

    @Schema(description = "매칭 증거(프론트 배지·툴팁용)")
    private MatchedEvidence matched;

    @Schema(name = "GuideRecommendReasonFact")
    @Getter
    @Builder
    public static class ReasonFact {
        @Schema(description = "ReasonGenerator 코드(예: REGION_MATCH, SOFT_ACTIVITY_PENALTY)")
        private String code;
        @Schema(description = "해당 근거에 대응하는 값 목록")
        private List<String> values;
    }

    @Schema(name = "GuideRecommendMatchedEvidence")
    @Getter
    @Builder
    public static class MatchedEvidence {
        private boolean region;
        private boolean style;
        @Schema(description = "예산 티어(낮음/중간/높음) 완전 일치")
        private boolean budget;
        @Schema(description = "예산 티어가 한 단계만 차이(인접). budget이 true이면 보통 false")
        private boolean budgetAdjacent;
        @Schema(description = "선호 활동 태그와 가이드 전문 태그 교집합(정규화)")
        private List<String> tags;
        @Schema(description = "선호 언어와 가이드 가능 언어 교집합")
        private List<String> languages;
        /**
         * 여행자 soft 부정 태그와 가이드 전문 태그 교집합(배지·툴팁용). 없으면 빈 목록.
         */
        @Schema(description = "soft 부정 태그 ∩ 가이드 전문 태그(점수 패널티가 난 활동)")
        private List<String> softPenaltyOverlapTags;
    }
}