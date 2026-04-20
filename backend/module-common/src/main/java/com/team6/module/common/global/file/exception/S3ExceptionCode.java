package com.team6.module.common.global.file.exception;

import com.team6.module.common.global.exception.ExceptionCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@Getter
@RequiredArgsConstructor
public enum S3ExceptionCode implements ExceptionCode {
    FILE_UPLOAD_FAILED(INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    FILE_DELETE_FAILED(INTERNAL_SERVER_ERROR, "파일을 삭제할 수 없습니다."),
    FILE_NOT_FOUND(BAD_REQUEST, "업로드할 파일이 없습니다."),
    INVALID_FILE_TYPE(BAD_REQUEST, "지원하지 않는 파일 타입입니다."),
    INVALID_FILE_URL(BAD_REQUEST, "유효하지 않은 파일 URL입니다."),
    EMPTY_FOLDER_NAME(BAD_REQUEST, "폴더명이 비어 있습니다.")
    ;

    private final HttpStatus status;

    private final String message;

    @Override
    public String getCode() {
        return this.name();
    }

}