package com.team6.domain.matching.entity.enums;

// RefundType.java
public enum RefundType {
    AUTO,          // 결제 후 2시간 이내 자동 환불
    MANUAL,        // 가이드 불만족으로 수동 신청
    CANCEL_GUEST,  // 게스트 취소로 인한 환불
    CANCEL_GUIDE   // 가이드 취소로 인한 환불
}