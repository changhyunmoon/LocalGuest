package com.team6.module.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * LLM이 반환하는 추천 요청 JSON과 1:1에 가깝게 맞춘 DTO(알 수 없는 키는 무시).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmGuideRecommendJson {

    private String region;
    private String travelStyle;
    private String budgetLevel;
    private Integer budgetMinWon;
    private Integer budgetMaxWon;
    private String budgetScope;
    private Boolean strictBudget;
    private String companionType;
    private List<String> activityTags;
    private List<String> requiredActivityTags;
    private List<String> niceToHaveActivityTags;
    private List<String> preferredLanguages;
    private List<String> requiredLanguages;
    private List<String> niceToHaveLanguages;
    private Boolean allowAdjacentRegion;
    private Integer headcount;
    private Integer durationDays;
    private List<String> excludedActivityTags;
    private List<String> excludedRegions;
    private List<String> excludedTravelStyles;
    private List<String> excludedLanguages;
    private List<String> softPenaltyActivityTags;
    /** 가이드에게 보여줄 짧은 불릿(반말 한 줄씩). */
    private List<String> guideBullets;
    /** 특별 요청 등 문단으로 전달할 내용. */
    private String specialRequests;
}
