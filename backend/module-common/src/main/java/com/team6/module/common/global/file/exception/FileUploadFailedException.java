package com.team6.module.common.global.file.exception;

import com.team6.module.common.global.exception.CustomException;

public class FileUploadFailedException extends CustomException {
    public FileUploadFailedException() {
        super(S3ExceptionCode.FILE_UPLOAD_FAILED);
    }
}
