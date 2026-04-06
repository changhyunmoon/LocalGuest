package com.team6.domain.matching.dto.response;

import com.team6.domain.matching.entity.MatchRequest;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MatchRequestActionResponse {

    private Long requestId;
    private String status;

    public static MatchRequestActionResponse from(MatchRequest entity) {
        return MatchRequestActionResponse.builder()
                .requestId(entity.getRequestId())
                .status(entity.getStatus().name())
                .build();
    }
}