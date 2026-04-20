package com.team6.domain.member.entity;

public enum GuideMatchingStyle {
    FRIENDLY("친근한 동행"),
    PROFESSIONAL("전문 가이드"),
    FLEXIBLE("유연한 스타일");

    private final String description;

    GuideMatchingStyle(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}