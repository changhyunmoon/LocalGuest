package com.team6.module.chat.dto.response;

public record LeaveChatRoomResponse(
        String roomId,
        String userEmail,
        boolean left,
        boolean roomDeleted
) {
}
