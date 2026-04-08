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
        /** soft 부정 제약(가이드 전문 태그와 겹치면 활동 점수 패널티). */
        private List<String> softPenaltyActivityTags;
        private List<String> preferredLanguages;
    }
}