package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideSchedule;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 가이드 스케줄 응답 DTO
 */
@Getter
@Builder
public class GuideScheduleResponse {

    private Long scheduleId;                // 스케줄 ID
    private Long guideId;                   // 가이드 프로필 ID
    private LocalDate availableDate;        // 가능 날짜
    private LocalTime startTime;            // 시작 시간
    private LocalTime endTime;              // 종료 시간
    private GuideScheduleStatus status;     // 스케줄 상태
    private Long matchRequestId;            // 매칭 요청 ID (예약 시 설정)
    private Boolean isPaid;                 // 결제 완료 여부
    private LocalDateTime createdAt;        // 등록일시
    private LocalDateTime updatedAt;        // 수정일시

    // Entity → DTO 변환
    public static GuideScheduleResponse from(GuideSchedule schedule) {
        return GuideScheduleResponse.builder()
                .scheduleId(schedule.getId())
                .guideId(schedule.getGuideProfile().getId())
                .availableDate(schedule.getAvailableDate())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .status(schedule.getStatus())
                .matchRequestId(schedule.getMatchRequestId())
                .isPaid(schedule.getIsPaid())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
