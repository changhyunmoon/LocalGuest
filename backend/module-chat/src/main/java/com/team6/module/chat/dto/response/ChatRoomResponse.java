package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mysql.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        String roomId,
        String title,
        Long ownerId,
        Integer participantCount,
        LocalDateTime createdAt
) {
    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return new ChatRoomResponse(
                chatRoom.getRoomId(),
                chatRoom.getTitle(),
                chatRoom.getOwnerId(),
                chatRoom.getParticipantCount(),
                chatRoom.getCreatedAt()
        );
    }
}
