package com.team6.module.ai.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TravelerPreference {
    private String region;
    private String travelStyle;
    private String budgetLevel;
    private Integer budgetMinWon;
    private Integer budgetMaxWon;
    private String budgetScope;
    private String companionType;
    private List<String> activityTags;
    /**
     * 파서가 강한 의도(꼭/반드시 등)로 읽은 활동 태그. 가이드 전문에 없으면 추가 감점.
     */
    private List<String> requiredActivityTags;
    /**
     * 파서가 약한 선호(되면 좋고 등)로 읽은 활동 태그. 매칭 시 활동 가중을 낮춘다.
     */
    private List<String> niceToHaveActivityTags;
    private List<String> requiredLanguages;
    private List<String> niceToHaveLanguages;
    private List<String> preferredLanguages;

    // 파서 확장용(미사용 필드라도 MVP에 영향 없이 확장 가능)
    private Integer headcount;
    private Integer durationDays;
    private List<String> excludedActivityTags;
    private List<String> excludedRegions;
    private List<String> excludedTravelStyles;
    private List<String> excludedLanguages;
    /** 활동 매칭 시 가이드 태그와 겹치면 점수 패널티(soft 부정 제약). */
    private List<String> softPenaltyActivityTags;

    /** 예산 힌트가 강한 의도(꼭/필수)인지. */
    private Boolean strictBudget;
}