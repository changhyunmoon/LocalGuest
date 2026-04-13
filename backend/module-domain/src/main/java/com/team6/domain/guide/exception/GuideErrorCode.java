package com.team6.domain.guide.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 가이드 도메인 에러 코드 enum
 */
@Getter
@RequiredArgsConstructor
public enum GuideErrorCode {

    // GuideProfile 관련
    GUIDE_NOT_FOUND(404, "가이드를 찾을 수 없습니다"),
    GUIDE_ALREADY_EXISTS(409, "이미 등록된 가이드입니다"),
    GUIDE_UNAUTHORIZED(403, "본인의 프로필만 수정할 수 있습니다"),

    // GuideImage 관련
    IMAGE_NOT_FOUND(404, "이미지를 찾을 수 없습니다"),

    // GuideSchedule 관련
    SCHEDULE_NOT_FOUND(404, "스케줄을 찾을 수 없습니다"),
    SCHEDULE_ALREADY_BOOKED(409, "이미 예약된 스케줄입니다"),
    SCHEDULE_NOT_AVAILABLE(409, "예약 가능 상태인 스케줄이 아닙니다"),
    SCHEDULE_NOT_PENDING(409, "대기 중인 스케줄이 아닙니다"),
    SCHEDULE_NOT_BOOKED(409, "예약 확정 상태인 스케줄이 아닙니다"),
    SCHEDULE_INVALID_TIME(400, "시작 시간은 종료 시간보다 이전이어야 합니다"),
    SCHEDULE_STATUS_INVALID(400, "AVAILABLE 또는 BLOCKED 상태로만 변경할 수 있습니다"),

    // GuideCareer 관련
    CAREER_NOT_FOUND(404, "경력을 찾을 수 없습니다"),

    // GuideFeed 관련
    FEED_NOT_FOUND(404, "피드를 찾을 수 없습니다");

    private final int status;     // HTTP 상태 코드
    private final String message; // 에러 메시지
}
