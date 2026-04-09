package com.team6.module.chat.exception;

import com.team6.module.common.global.exception.CustomException;

public class ParticipantNotFoundException extends CustomException {
    public ParticipantNotFoundException() {
        super(ChatExceptionCode.PARTICIPANT_NOT_FOUND);
    }
}
