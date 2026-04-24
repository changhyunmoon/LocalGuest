package com.team6.module.ai.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    /**
     * LLM이 종종 문자열 대신 배열로 주는 경우가 있어 유연하게 받는다.
     * <p>예: "가족" 또는 ["가족"].
     */
    @JsonProperty("companionType")
    private Object companionTypeRaw;
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
    /** 가이드에게 보여줄 서술형 요약(한두 문장). LLM 시스템 프롬프트에서 우선 채우도록 유도한다. */
    private String specialRequests;

    public String getCompanionType() {
        if (companionTypeRaw == null) {
            return null;
        }
        if (companionTypeRaw instanceof String s) {
            return s;
        }
        if (companionTypeRaw instanceof List<?> list) {
            for (Object v : list) {
                if (v instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
            return null;
        }
        return String.valueOf(companionTypeRaw);
    }

    public void setCompanionType(String companionType) {
        this.companionTypeRaw = companionType;
    }
}
