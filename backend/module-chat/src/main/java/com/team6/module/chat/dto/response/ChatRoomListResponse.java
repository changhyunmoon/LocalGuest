package com.team6.module.chat.dto.response;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record ChatRoomListResponse(
        String roomId,
        String title,
        Integer participantCount,
        String lastMessage,
        LocalDateTime lastMessageAt,
        Long unreadCount
) {

}