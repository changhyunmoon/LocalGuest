package com.team6.module.chat.dto.response;


import com.team6.module.chat.entity.mysql.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,                // MySQL PK
        String roomId,          // 비즈니스 UUID
        String title,           // 방 제목
        Integer participantCount, // 현재 참여 인원수
        String lastMessage,     // 마지막 메시지
        LocalDateTime lastMessageAt // 마지막 메시지 시간
) {
    public static ChatRoomResponse from(ChatRoom entity) {
        return new ChatRoomResponse(
                entity.getId(),
                entity.getRoomId(),
                entity.getTitle(),
                entity.getParticipantCount(),
                entity.getLastMessage(),
                entity.getLastMessageAt()
        );
    }
}