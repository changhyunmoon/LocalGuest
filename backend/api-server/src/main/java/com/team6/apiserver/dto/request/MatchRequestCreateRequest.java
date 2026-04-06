package com.team6.apiserver.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class MatchRequestCreateRequest {

    @NotNull
    private Long guideId;

    @NotBlank
    private String destination;

    private String concept;

    private LocalDate desiredDate;

    private Integer desiredBudget;
}