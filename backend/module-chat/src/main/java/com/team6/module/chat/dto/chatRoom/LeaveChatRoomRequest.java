package com.team6.module.chat.dto.chatRoom;

import jakarta.validation.constraints.NotBlank;

public record LeaveChatRoomRequest(
        @NotBlank(message = "채팅방 ID는 필수입니다.")
        String roomId
) {
}
