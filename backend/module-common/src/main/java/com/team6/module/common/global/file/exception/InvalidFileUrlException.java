package com.team6.module.common.global.file.exception;

import com.team6.module.common.global.exception.CustomException;

public class InvalidFileUrlException extends CustomException {
    public InvalidFileUrlException() {
        super(S3ExceptionCode.INVALID_FILE_URL);
    }
}