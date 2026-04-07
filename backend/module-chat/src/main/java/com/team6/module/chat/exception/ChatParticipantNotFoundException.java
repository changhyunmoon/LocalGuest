package com.team6.module.chat.exception;

import com.team6.module.common.global.exception.CustomException;

public class ChatParticipantNotFoundException extends CustomException {
    public ChatParticipantNotFoundException() {
        super(ChatExceptionCode.CHAT_PARTICIPANT_NOT_FOUND);
    }
}
