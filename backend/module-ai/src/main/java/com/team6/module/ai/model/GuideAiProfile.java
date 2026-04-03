package com.team6.module.ai.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GuideAiProfile {
    private Long guideId;
    private String guideName;
    private String region;
    private String guideStyle;
    private String priceLevel;
    private List<String> specialtyTags;
    private List<String> languages;
}