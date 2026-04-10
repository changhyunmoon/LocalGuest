package com.team6.domain.matching.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class MatchRequestCreateRequest {

    @NotNull
    /**
     * 가이드 식별자 (guide_profiles.id)
     * member.id가 아닌 guide_profiles PK를 전달해야 한다.
     */
    private Long guideId;

    @NotBlank
    private String destination;

    private String concept;

    /**
     * AI가 정리한 컨셉 요약 (선택).
     * 프론트에서 전달하지 않으면 서버에서 destination/concept 등을 기반으로 생성한다.
     */
    private String conceptSummary;

    private LocalDate desiredDate;

    private Integer desiredBudget;
}