package com.team6.domain.member.entity;

public enum InterestRegion {
    SEOUL("서울"),
    BUSAN("부산"),
    JEJU("제주"),
    GANGWON("강원"),
    GYEONGGI("경기"),
    GYEONGSANG("경상"),
    JEOLLA("전라"),
    CHUNGCHEONG("충청");

    private final String description;

    InterestRegion(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}