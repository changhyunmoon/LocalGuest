package com.team6.module.chat.exception;

import com.team6.module.common.global.exception.CustomException;

public class ChatRoomNotFoundException extends CustomException {
    public ChatRoomNotFoundException() {
        super(ChatExceptionCode.CHAT_ROOM_NOT_FOUND);
    }
}