package com.team6.apiserver.constant;

public enum MatchStatus {
    REQUESTED,        // 게스트가 가이드에게 매칭 요청 보냄
    GUIDE_ACCEPTED,   // 가이드가 요청 수락
    GUIDE_REJECTED,   // 가이드가 요청 거절
    GUEST_CONFIRMED,  // 게스트가 최종 수락
    GUEST_REJECTED    // 게스트가 최종 거절
}