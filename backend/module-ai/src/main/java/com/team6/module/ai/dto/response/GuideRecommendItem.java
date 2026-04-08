package com.team6.module.ai.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GuideRecommendItem {
    private Long guideId;
    private String guideName;
    private int score;
    private String reason;

    private MatchedEvidence matched;

    @Getter
    @Builder
    public static class MatchedEvidence {
        private boolean region;
        private boolean style;
        private boolean budget;
        private List<String> tags;
        private List<String> languages;
    }
}