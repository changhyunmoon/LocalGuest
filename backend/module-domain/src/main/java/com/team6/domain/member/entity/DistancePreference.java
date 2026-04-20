package com.team6.domain.member.entity;

public enum DistancePreference {
    NEARBY("가까운 곳"),
    DOMESTIC("국내"),
    INTERNATIONAL("해외");

    private final String description;

    DistancePreference(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}