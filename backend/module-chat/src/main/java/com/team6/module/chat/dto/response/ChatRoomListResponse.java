package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mysql.ChatRoom;

public record ChatRoomListResponse(
        String roomId,
        String title,
        String lastMessage,
        Integer participantCount,
        java.time.LocalDateTime lastMessageAt
) {
    public static ChatRoomListResponse from(ChatRoom chatRoom) {
        return new ChatRoomListResponse(
                chatRoom.getRoomId(),
                chatRoom.getTitle(),
                chatRoom.getLastMessage(),
                chatRoom.getParticipantCount(),
                chatRoom.getLastMessageAt()
        );
    }
}