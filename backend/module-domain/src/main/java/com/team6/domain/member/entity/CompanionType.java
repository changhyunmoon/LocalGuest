package com.team6.domain.member.entity;

public enum CompanionType {
    ALONE("나 홀로"),
    FRIENDS("친구·연인"),
    FAMILY("가족");

    private final String description;

    CompanionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}