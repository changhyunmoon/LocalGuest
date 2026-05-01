package com.team6.module.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomSummaryResponse> rooms
) {
    public record ChatRoomSummaryResponse(
            String roomId,
            String title,
            String lastMessage,
            LocalDateTime lastMessageAt,
            Integer participantCount
    ) {
    }
}