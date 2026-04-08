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
    private String companionType;
    private List<String> activityTags;
    private List<String> preferredLanguages;

    // 파서 확장용(미사용 필드라도 MVP에 영향 없이 확장 가능)
    private Integer headcount;
    private Integer durationDays;
    private List<String> excludedActivityTags;
    /** 활동 매칭 시 가이드 태그와 겹치면 점수 패널티(soft 부정 제약). */
    private List<String> softPenaltyActivityTags;
}