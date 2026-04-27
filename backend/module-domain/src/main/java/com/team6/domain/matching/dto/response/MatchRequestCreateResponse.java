package com.team6.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team6.domain.matching.entity.MatchRequest;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class MatchRequestCreateResponse {

    private Long requestId;
    private Long guestId;
    private Long guideId;
    @Getter(AccessLevel.NONE)
    private Long guideScheduleId;
    private String destination;
    private String concept;
    private String conceptSummary;
    private LocalDate desiredDate;
    private Integer desiredBudget;
    private Integer budgetMinWon;
    private Integer budgetMaxWon;
    private String proposedSchedule;
    private String proposeMessage;
    private String status;
    private LocalDateTime createdAt;

    @JsonProperty("scheduleId")
    public Long getGuideScheduleId() {
        return guideScheduleId;
    }

    public static MatchRequestCreateResponse from(MatchRequest entity) {
        return MatchRequestCreateResponse.builder()
                .requestId(entity.getId())
                .guestId(entity.getGuestId())
                .guideId(entity.getGuideId())
                .guideScheduleId(entity.getGuideScheduleId())
                .destination(entity.getDestination())
                .concept(entity.getConcept())
                .conceptSummary(entity.getConceptSummary())
                .desiredDate(entity.getDesiredDate())
                .desiredBudget(entity.getDesiredBudget())
                .budgetMinWon(entity.getBudgetMinWon())
                .budgetMaxWon(entity.getBudgetMaxWon())
                .proposedSchedule(entity.getProposedSchedule())
                .proposeMessage(entity.getProposeMessage())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static MatchRequestCreateResponse fromProjection(MatchRequestListProjection projection) {
        return MatchRequestCreateResponse.builder()
                .requestId(projection.getRequestId())
                .guestId(projection.getGuestId())
                .guideId(projection.getGuideId())
                .guideScheduleId(projection.getGuideScheduleId())
                .destination(projection.getDestination())
                .concept(projection.getConcept())
                .conceptSummary(projection.getConceptSummary())
                .desiredDate(projection.getDesiredDate())
                .desiredBudget(projection.getDesiredBudget())
                .budgetMinWon(projection.getBudgetMinWon())
                .budgetMaxWon(projection.getBudgetMaxWon())
                .proposedSchedule(projection.getProposedSchedule())
                .proposeMessage(projection.getProposeMessage())
                .status(projection.getStatus() == null ? null : projection.getStatus().name())
                .createdAt(projection.getCreatedAt())
                .build();
    }
}