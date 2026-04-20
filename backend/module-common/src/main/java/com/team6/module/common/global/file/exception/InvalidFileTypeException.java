package com.team6.module.common.global.file.exception;

import com.team6.module.common.global.exception.CustomException;

public class InvalidFileTypeException extends CustomException {
    public InvalidFileTypeException() {
        super(S3ExceptionCode.INVALID_FILE_TYPE);
    }
}