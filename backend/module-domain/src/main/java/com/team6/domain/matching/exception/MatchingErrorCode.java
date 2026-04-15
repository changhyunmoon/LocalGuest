package com.team6.domain.matching.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MatchingErrorCode {
    // MatchRequest 관련
    MATCH_REQUEST_NOT_FOUND(404, "매칭 요청을 찾을 수 없습니다"),
    MATCH_REQUEST_CANNOT_CANCEL(400, "현재 상태에서는 취소할 수 없습니다"),
    MATCH_REQUEST_UNAUTHORIZED(403, "해당 매칭 요청에 대한 권한이 없습니다"),
    MATCH_REQUEST_INVALID_STATUS(400, "현재 상태에서는 처리할 수 없습니다"),

    // Payment 관련
    PAYMENT_NOT_FOUND(404, "결제 정보를 찾을 수 없습니다"),
    PAYMENT_ALREADY_CANCELLED(400, "이미 취소된 결제입니다"),
    PAYMENT_NOT_COMPLETED(400, "완료된 결제가 아닙니다"),
    PAYMENT_NOT_PENDING(400, "대기 중인 결제가 아닙니다"),
    PAYMENT_ALREADY_COMPLETED(400, "이미 완료된 결제입니다"),
    PAYMENT_DUPLICATE_TYPE(400, "해당 매칭 요청에 동일 유형의 결제가 이미 존재합니다"),
    PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 일치하지 않습니다"),
    PAYMENT_PG_VERIFICATION_FAILED(400, "PG 승인 검증에 실패했습니다"),
    GUIDE_SCHEDULE_SYNC_FAILED(502, "가이드 스케줄 상태 동기화에 실패했습니다"),

    // Refund 관련
    REFUND_DEADLINE_EXCEEDED(400, "환불 가능 시간(2시간)이 초과되었습니다"),
    REFUND_ALREADY_REQUESTED(400, "이미 환불 신청된 결제입니다"),
    REFUND_NOT_FOUND(404, "환불 신청을 찾을 수 없습니다"),

    // TourExtension 관련
    TOUR_EXTENSION_NOT_FOUND(404, "연장 신청을 찾을 수 없습니다"),
    TOUR_EXTENSION_DEADLINE_EXCEEDED(400, "연장 선택 기한이 지났습니다"),
    TOUR_EXTENSION_ALREADY_REQUESTED(400, "이미 연장 신청된 투어입니다"),
    TOUR_EXTENSION_CANNOT_APPROVE(400, "현재 상태에서는 승인할 수 없습니다"),

    INVALID_REQUEST(400, "잘못된 요청입니다"),
    GUEST_GUIDE_SAME(400, "게스트와 가이드가 동일인입니다");

    private final int status;    // HTTP 상태코드
    private final String message; // 에러 메시지
}

