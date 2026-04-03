package com.team6.domain.matching.entity.enums;

// PaymentStatus.java
public enum PaymentStatus {
    PENDING,    // 결제 요청됨, PG 처리 대기
    COMPLETED,  // 결제 정상 완료
    REFUNDED,   // 환불 완료
    FAILED,     // 결제 실패 (PG 오류 등)
    CANCELLED   // 취소 처리됨
}