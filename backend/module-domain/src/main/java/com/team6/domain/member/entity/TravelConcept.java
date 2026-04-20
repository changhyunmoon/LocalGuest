package com.team6.domain.member.entity;

public enum TravelConcept {
    NATURE("자연 위주"),
    CULTURE("문화 탐방"),
    FOOD("맛집 투어"),
    ACTIVITY("액티비티"),
    HEALING("힐링·휴식"),
    SHOPPING("쇼핑"),
    PHOTO("사진·인생샷"),
    LOCAL_LIFE("로컬 생활"),
    ADVENTURE("모험·탐험");

    private final String description;

    TravelConcept(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}