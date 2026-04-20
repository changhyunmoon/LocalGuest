package com.team6.module.common.global.file.exception;

import com.team6.module.common.global.exception.CustomException;

public class EmptyFolderNameException extends CustomException {
    public EmptyFolderNameException() {
        super(S3ExceptionCode.EMPTY_FOLDER_NAME);
    }
}