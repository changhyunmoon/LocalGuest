package com.team6.domain.guestMyPage.dto.response;

import com.team6.domain.matching.entity.MatchRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class UpcomingMatchResponse {
    private Long matchRequestedId;
    private Long guideId;
    private String destination;
    private LocalDate desiredDate;
    private String status;

    public static UpcomingMatchResponse from(MatchRequest matchRequest) {
        return UpcomingMatchResponse.builder()
                .matchRequestedId(matchRequest.getId())
                .guideId(matchRequest.getGuideId())
                .destination(matchRequest.getDestination())
                .desiredDate(matchRequest.getDesiredDate())
                .status(matchRequest.getStatus().name())
                .build();
    }
}
