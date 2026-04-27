package com.team6.domain.matching.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MatchRequestDeclineRequest {

    @NotBlank
    private String reason;
}

