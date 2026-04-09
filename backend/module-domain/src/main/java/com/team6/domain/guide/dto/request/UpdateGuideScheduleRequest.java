package com.team6.domain.guide.dto.request;

import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 가이드 스케줄 수정 요청 DTO (F06-04)
 */
@Getter
@NoArgsConstructor
public class UpdateGuideScheduleRequest {

    @NotNull
    private LocalDate availableDate;      // 가능 날짜

    @NotNull
    private LocalTime startTime;          // 시작 시간

    @NotNull
    private LocalTime endTime;            // 종료 시간

    private GuideScheduleStatus status;   // 스케줄 상태 (선택 — null이면 기존 상태 유지)
}
