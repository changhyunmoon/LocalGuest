package com.team6.domain.matching.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    /**
     * 가이드 스케줄 식별자 (guide_schedules.id)
     * 매칭 생성 시 해당 슬롯을 PENDING으로 잠그기 위해 전달한다.
     * JSON: {@code scheduleId} (가이드 API와 동일). {@code guideScheduleId}도 수신 호환.
     */
    @Positive
    @JsonProperty("scheduleId")
    @JsonAlias("guideScheduleId")
    private Long guideScheduleId;

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

    /**
     * 희망 예산 범위(원). 프롬프트/입력에서 범위를 받는 경우 그대로 저장한다(선택).
     */
    private Integer budgetMinWon;
    private Integer budgetMaxWon;
}
