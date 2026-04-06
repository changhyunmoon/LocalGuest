package com.team6.domain.matching.dto.response;

import com.team6.domain.matching.entity.MatchRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class MatchRequestSummaryResponse {

    private Long requestId;
    private Long guestId;
    private Long guideId;
    private String destination;
    private String concept;
    private LocalDate desiredDate;
    private Integer desiredBudget;
    private String status;
    private LocalDateTime createdAt;

    public static MatchRequestSummaryResponse from(MatchRequest entity) {
        return MatchRequestSummaryResponse.builder()
                .requestId(entity.getRequestId())
                .guestId(entity.getGuestId())
                .guideId(entity.getGuideId())
                .destination(entity.getDestination())
                .concept(entity.getConcept())
                .desiredDate(entity.getDesiredDate())
                .desiredBudget(entity.getDesiredBudget())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}