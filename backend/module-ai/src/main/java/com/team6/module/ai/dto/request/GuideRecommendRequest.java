package com.team6.module.ai.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GuideRecommendRequest {
    private String region;
    private String travelStyle;
    private String budgetLevel;
    private String companionType;
    private List<String> activityTags;
    private List<String> preferredLanguages;
    private Integer topN;
    private List<GuideCandidateDto> guideCandidates;

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
    }
}