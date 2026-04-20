package com.team6.domain.member.entity;

public enum PlanningStyle {
    WELL_PLANNED("철저한 계획"),
    MODERATE("중간 자유여행"),
    SPONTANEOUS("자유로운 즉흥");

    private final String description;

    PlanningStyle(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}