package com.team6.domain.matching.entity.enums;

// TourExtensionStatus.java
public enum TourExtensionStatus {
    REQUESTED,       // 게스트가 연장 신청함
    GUIDE_APPROVED,  // 가이드 승인 완료, 게스트 결제 대기
    PAID,            // 연장 결제 완료
    REJECTED,        // 가이드가 연장 거절함
    AUTO_CANCELLED   // 23:59:59까지 미선택으로 자동 취소
}