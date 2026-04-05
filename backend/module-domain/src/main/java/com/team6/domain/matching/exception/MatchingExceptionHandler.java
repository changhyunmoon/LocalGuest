package com.team6.domain.matching.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class MatchingExceptionHandler {

    @ExceptionHandler(MatchingException.class)
    public ResponseEntity<Map<String, Object>> handleMatchingException(MatchingException e) {
        log.warn("[MatchingException] status={}, message={}",
                e.getErrorCode().getStatus(), e.getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(Map.of(
                        "status", e.getErrorCode().getStatus(),
                        "message", e.getMessage()
                ));
    }
}