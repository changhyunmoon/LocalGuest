package com.team6.domain.matching.dto.response;

import com.team6.domain.matching.entity.MatchRequest;
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
    private String destination;
    private String concept;
    private String conceptSummary;
    private LocalDate desiredDate;
    private Integer desiredBudget;
    private String proposedSchedule;
    private String proposeMessage;
    private String status;
    private LocalDateTime createdAt;

    public static MatchRequestCreateResponse from(MatchRequest entity) {
        return MatchRequestCreateResponse.builder()
                .requestId(entity.getId())
                .guestId(entity.getGuestId())
                .guideId(entity.getGuideId())
                .destination(entity.getDestination())
                .concept(entity.getConcept())
                .conceptSummary(entity.getConceptSummary())
                .desiredDate(entity.getDesiredDate())
                .desiredBudget(entity.getDesiredBudget())
                .proposedSchedule(entity.getProposedSchedule())
                .proposeMessage(entity.getProposeMessage())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}