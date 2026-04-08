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
    /**
     * 구조화된 추천 근거 코드(표시된 상위 근거와 동일 순서, 최대 3개).
     */
    private List<String> reasonCodes;
    /**
     * 근거 코드별 매칭 값(예: 태그/언어 목록).
     */
    private List<ReasonFact> reasonFacts;

    private MatchedEvidence matched;

    @Getter
    @Builder
    public static class ReasonFact {
        private String code;
        private List<String> values;
    }

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