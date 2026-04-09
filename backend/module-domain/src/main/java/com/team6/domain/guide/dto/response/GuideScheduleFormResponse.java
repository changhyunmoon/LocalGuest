package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideSchedule;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 가이드 스케줄 수락 시 반환되는 양식 응답 DTO (F06-04)
 * 가이드가 수락 후 작성할 안내 정보를 담는 새 페이지용 양식
 */
@Getter
@Builder
public class GuideScheduleFormResponse {

    private Long scheduleId;        // 스케줄 ID
    private Long matchRequestId;    // 매칭 요청 ID (연동용)
    private LocalDate availableDate; // 투어 날짜
    private LocalTime startTime;    // 시작 시간
    private LocalTime endTime;      // 종료 시간
    private String meetingPoint;    // 만남 장소 (가이드 작성 예정, 초기값 빈 문자열)
    private String guideMessage;    // 가이드 안내 메시지 (가이드 작성 예정, 초기값 빈 문자열)
    private String courseDetail;    // 코스 상세 정보 (가이드 작성 예정, 초기값 빈 문자열)

    // Entity → 수락 양식 DTO 변환 (작성 필드는 빈 문자열로 초기화)
    public static GuideScheduleFormResponse from(GuideSchedule schedule) {
        return GuideScheduleFormResponse.builder()
                .scheduleId(schedule.getId())
                .matchRequestId(schedule.getMatchRequestId())
                .availableDate(schedule.getAvailableDate())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .meetingPoint("")   // 가이드가 이후 작성할 항목
                .guideMessage("")   // 가이드가 이후 작성할 항목
                .courseDetail("")   // 가이드가 이후 작성할 항목
                .build();
    }
}
