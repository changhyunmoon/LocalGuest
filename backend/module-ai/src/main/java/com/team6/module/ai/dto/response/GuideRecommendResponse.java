package com.team6.module.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(name = "GuideRecommendResponse", description = "프롬프트/추천 요청에 대한 응답")
@Getter
@Builder
public class GuideRecommendResponse {
    @Schema(description = "한 줄 요약(컨셉·일정·예산 등)")
    private String conceptSummary;
    @Schema(description = "파싱된 키워드 묶음")
    private Keywords keywords;
    @Schema(description = "사용자 안내(지역 누락, 인접 지역 포함, 희소 풀 등)")
    private String notice;
    @Schema(
            description = "notice에 대응하는 기계 판별 코드(프론트 i18n·토스트 매핑). 중복 없이 순서 유지.",
            example = "[\"ADJACENT_REGION_INCLUDED\", \"SPARSE_GUIDE_POOL\"]"
    )
    private List<String> noticeCodes;
    /**
     * 룰 기반 추천 정책 버전({@link com.team6.module.ai.support.AiRecommendationTuning#POLICY_VERSION}).
     */
    @Schema(description = "룰 기반 추천 정책 버전(동일 프롬프트라도 정책 변경 구분용)", example = "2026.04.8")
    private String policyVersion;
    @Schema(description = "추천 항목 개수")
    private int totalCount;
    @Schema(description = "정렬된 추천 목록")
    private List<GuideRecommendItem> recommendations;

    @Schema(name = "GuideRecommendResponseKeywords", description = "프롬프트에서 추출한 키워드")
    @Getter
    @Builder
    public static class Keywords {
        private String region;
        private String travelStyle;
        private String budgetLevel;
        private String companionType;
        private Integer headcount;
        private Integer durationDays;
        private List<String> activityTags;
        @Schema(description = "하드 제외 활동 태그")
        private List<String> excludedActivityTags;
        @Schema(description = "soft 부정: 가이드 전문 태그와 겹치면 활동 점수에 패널티")
        private List<String> softPenaltyActivityTags;
        private List<String> preferredLanguages;
    }
}