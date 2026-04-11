package com.team6.module.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "GuideRecommendRequest", description = "파싱 결과 또는 직접 구성한 추천 요청(내부/테스트용)")
@Getter
@Builder
public class GuideRecommendRequest {
    private String region;
    private String travelStyle;
    private String budgetLevel;
    private String companionType;
    private List<String> activityTags;
    private List<String> preferredLanguages;
    private Integer headcount;
    private Integer durationDays;
    private List<String> excludedActivityTags;
    /**
     * 하드 제외가 아니라, 가이드가 해당 활동을 강하게 전문으로 내세울 때 점수를 깎는(soft) 태그.
     */
    private List<String> softPenaltyActivityTags;
    private Integer topN;
    private List<GuideCandidateDto> guideCandidates;

    @Schema(name = "GuideCandidateDto", description = "추천 후보 가이드 프로필")
    @Getter
    @Builder
    public static class GuideCandidateDto {
        private Long guideId;
        private String guideName;
        private String region;
        private String guideStyle;
        private String priceLevel;
        private List<String> specialtyTags;
        private List<String> languages;
        /** 도메인 집계: 평균 평점(없으면 null — 감점 미적용). */
        @Schema(description = "가이드 평균 평점(리뷰 집계). 미전달 시 피드백 감점에 쓰이지 않음")
        private BigDecimal averageRating;
        @Schema(description = "리뷰 수. averageRating과 함께 쓰여 저평점 감점 여부를 판단")
        private Integer reviewCount;
        /** 승인된 환불 건수(가이드 기준). 미전달 시 환불 감점 미적용. */
        @Schema(description = "승인(APPROVED) 환불 건수. 집계해 전달하면 룰 기반 감점에 반영")
        private Integer approvedRefundCount;
    }
}