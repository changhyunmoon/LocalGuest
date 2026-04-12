package com.team6.module.chat.dto.chatMessage;


import com.team6.module.chat.entity.mongodb.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageResponse(
        String id, // MongoDB의 ID
        String roomId,
        String senderEmail,
        String senderNickname,
        String message,
        int unreadCount,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage entity) {
        return new ChatMessageResponse(
                entity.getId(),
                entity.getRoomId(),
                entity.getSenderEmail(),
                entity.getSenderNickname(),
                entity.getMessage(),
                entity.getUnreadCount(),
                entity.getCreatedAt()
        );
    }
}