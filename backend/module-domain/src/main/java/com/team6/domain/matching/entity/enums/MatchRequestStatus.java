package com.team6.domain.matching.entity.enums;

// MatchRequestStatus.java
public enum MatchRequestStatus {
    PENDING,      // 요청 생성됨, 가이드 응답 대기
    ACCEPTED,     // 게스트가 제안서 수락함
    PAID,         // 결제 완료, 투어 확정
    IN_PROGRESS,  // 투어 진행 중
    COMPLETED,    // 투어 정상 완료
    CANCELLED,    // 게스트 또는 가이드가 취소함
    REJECTED      // 가이드가 요청 거절함
}