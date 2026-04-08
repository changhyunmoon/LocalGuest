package com.team6.module.ai.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GuideRecommendResponse {
    private String conceptSummary;
    private Keywords keywords;
    private String notice;
    private int totalCount;
    private List<GuideRecommendItem> recommendations;

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
        private List<String> excludedActivityTags;
        private List<String> preferredLanguages;
    }
}