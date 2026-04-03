package com.team6.module.ai.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GuideRecommendItem {
    private Long guideId;
    private String guideName;
    private int score;
    private String reason;
}