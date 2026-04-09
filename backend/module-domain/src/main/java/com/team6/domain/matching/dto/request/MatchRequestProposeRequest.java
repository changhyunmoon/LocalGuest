package com.team6.domain.matching.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MatchRequestProposeRequest {

    @NotBlank
    private String proposedSchedule;

    private String proposeMessage;
}

