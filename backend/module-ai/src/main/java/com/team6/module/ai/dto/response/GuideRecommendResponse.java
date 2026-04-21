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
    @Schema(description = "매칭 요청 화면에서 재사용하기 쉬운 AI 정리 초안")
    private MatchRequestDraft matchRequestDraft;
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
    @Schema(description = "룰 기반 추천 정책 버전(동일 프롬프트라도 정책 변경 구분용)", example = "2026.04.10")
    private String policyVersion;
    /**
     * 프롬프트에서 뽑은 신호(스타일·예산·활동 등)가 얼마나 풍부한지에 대한 룰 기반 등급.
     */
    @Schema(description = "파싱 신호 풍부도(룰 기반). LOW면 추가 질문 권장", allowableValues = {"LOW", "MEDIUM", "HIGH"})
    private String promptParseConfidence;
    @Schema(description = "추천 항목 개수")
    private int totalCount;
    @Schema(description = "정렬된 추천 목록")
    private List<GuideRecommendItem> recommendations;

    @Schema(description = "일정 충돌로 메인 추천에서 제외됐지만, 조건 매칭이 좋아 참고로 보여주는 1개 제시(선택)")
    private SpecialSuggestion specialSuggestion;

    @Schema(name = "GuideRecommendResponseSpecialSuggestion", description = "특별 제시(메인 추천과 분리된 참고용 제시)")
    @Getter
    @Builder
    public static class SpecialSuggestion {
        @Schema(description = "참고로 보여주는 가이드 1명")
        private GuideRecommendItem guide;
        @Schema(description = "특별 제시 안내 문구")
        private String notice;
    }

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

    @Schema(name = "GuideRecommendResponseMatchRequestDraft", description = "매칭 요청 폼 재사용용 AI 초안")
    @Getter
    @Builder
    public static class MatchRequestDraft {
        @Schema(description = "목적지 기본값")
        private String destination;
        @Schema(description = "가이드에게 전달할 컨셉 원문 초안")
        private String concept;
        @Schema(description = "가이드에게 보여줄 요약 문장")
        private String conceptSummary;
        @Schema(description = "예산 힌트(낮음/중간/높음)")
        private String budgetHint;
        private Integer headcount;
        private Integer durationDays;
        private String travelStyle;
        private String companionType;
        private List<String> activityTags;
        private List<String> excludedActivityTags;
        private List<String> preferredLanguages;
    }
}
