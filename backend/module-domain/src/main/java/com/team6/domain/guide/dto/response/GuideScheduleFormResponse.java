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

    private Long scheduleId;         // 스케줄 ID
    private Long matchRequestId;     // 매칭 요청 ID (연동용)
    private LocalDate availableDate; // 투어 날짜
    private LocalTime startTime;     // 시작 시간
    private LocalTime endTime;       // 종료 시간
    private String meetingPoint;     // 만남 장소
    private String guideMessage;     // 가이드 안내 메시지
    private String courseDetail;     // 코스 상세 — 결제 완료(isPaid=true) 전까지 null 반환
    private Boolean isPaid;          // 결제 완료 여부 (프론트 잠금 처리 기준)

    // Entity → 양식 응답 DTO 변환
    public static GuideScheduleFormResponse from(GuideSchedule schedule, boolean exposeCourseDetail, String courseDetailOverride) {
        boolean paid = Boolean.TRUE.equals(schedule.getIsPaid());
        return GuideScheduleFormResponse.builder()
                .scheduleId(schedule.getId())
                .matchRequestId(schedule.getMatchRequestId())
                .availableDate(schedule.getAvailableDate())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .meetingPoint(schedule.getMeetingPoint() != null ? schedule.getMeetingPoint() : "")
                .guideMessage(schedule.getGuideMessage() != null ? schedule.getGuideMessage() : "")
                .courseDetail(exposeCourseDetail ? (courseDetailOverride != null ? courseDetailOverride : schedule.getCourseDetail()) : null)
                .isPaid(paid)
                .build();
    }

    public static GuideScheduleFormResponse from(GuideSchedule schedule, boolean exposeCourseDetail) {
        return from(schedule, exposeCourseDetail, null);
    }

    /**
     * @deprecated 공개 정책 판단은 서비스에서 수행하고, {@link #from(GuideSchedule, boolean)}를 사용한다.
     */
    @Deprecated
    public static GuideScheduleFormResponse from(GuideSchedule schedule) {
        boolean paid = Boolean.TRUE.equals(schedule.getIsPaid());
        return from(schedule, paid);
    }
}
