package com.team6.domain.guide.entity.enums;

/**
 * 가이드 스케줄 상태 enum
 */
public enum GuideScheduleStatus {

    AVAILABLE,  // 예약 가능
    PENDING,    // 게스트 요청 대기 중
    BOOKED,     // 예약됨 (매칭 연동 시 사용)
    BLOCKED     // 가이드가 직접 막아둔 날
}
