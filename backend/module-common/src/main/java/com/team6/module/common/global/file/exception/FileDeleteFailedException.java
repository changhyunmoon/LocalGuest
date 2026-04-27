package com.team6.module.common.global.file.exception;

import com.team6.module.common.global.exception.CustomException;

public class FileDeleteFailedException extends CustomException {
    public FileDeleteFailedException() {
        super(S3ExceptionCode.FILE_DELETE_FAILED);
    }
}