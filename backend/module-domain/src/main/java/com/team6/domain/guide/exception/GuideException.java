package com.team6.domain.guide.exception;

import lombok.Getter;

/**
 * 가이드 도메인 커스텀 예외 클래스
 */
@Getter
public class GuideException extends RuntimeException {

    private final GuideErrorCode errorCode; // 에러 코드 (상태코드 + 메시지 포함)

    public GuideException(GuideErrorCode errorCode) {
        super(errorCode.getMessage()); // RuntimeException에 메시지 전달
        this.errorCode = errorCode;
    }
}
