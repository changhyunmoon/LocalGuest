package com.team6.module.chat.exception;

import com.team6.module.common.global.exception.CustomException;

public class ChatRoomNotFoundException extends CustomException {

    public ChatRoomNotFoundException() {
        super(ChatExceptionCode.CHATROOM_NOT_FOUND);
    }
}
