package com.team6.module.ai.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GuideRecommendResponse {
    private int totalCount;
    private List<GuideRecommendItem> recommendations;
}