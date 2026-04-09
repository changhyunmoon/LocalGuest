package com.team6.domain.guide.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 가이드 도메인 예외 핸들러
 */
@Slf4j
@Order(1) // GlobalExceptionHandler보다 먼저 처리되도록 우선순위 지정
@RestControllerAdvice
public class GuideExceptionHandler {

    // GuideException 발생 시 에러 코드 기준으로 HTTP 응답 반환
    @ExceptionHandler(GuideException.class)
    public ResponseEntity<Map<String, Object>> handleGuideException(GuideException e) {
        log.warn("[GuideException] status={}, message={}",
                e.getErrorCode().getStatus(), e.getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(Map.of(
                        "status", e.getErrorCode().getStatus(),   // HTTP 상태 코드
                        "message", e.getMessage()                  // 에러 메시지
                ));
    }
}
