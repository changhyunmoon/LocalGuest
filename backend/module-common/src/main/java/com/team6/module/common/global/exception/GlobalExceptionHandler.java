package com.team6.module.common.global.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Objects;
import java.util.stream.Collectors;

import static com.team6.module.common.global.exception.GlobalExceptionCode.INVALID_INPUT;
import static com.team6.module.common.global.exception.GlobalExceptionCode.SERVER_ERROR;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ExceptionResponse> handleCustomException(CustomException exception) {
        ExceptionResponse response = ExceptionResponse.from(exception);
        return ResponseEntity.status(response.status()).body(response);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ExceptionResponse> handleException(Exception exception) {
        log.error("Unexpected error occurred", exception);
        return ResponseEntity.internalServerError().body(ExceptionResponse.from(SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String message = exception.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        ExceptionResponse response = ExceptionResponse.of(
                INVALID_INPUT.getStatus(),
                INVALID_INPUT.getCode(), message
        );

        return ResponseEntity.status(response.status()).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String message = exception.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ExceptionResponse response = ExceptionResponse.of(
                INVALID_INPUT.getStatus(),
                INVALID_INPUT.getCode(), message
        );

        return ResponseEntity.status(response.status()).body(response);
    }

}