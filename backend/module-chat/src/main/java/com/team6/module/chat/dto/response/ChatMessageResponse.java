package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mongodb.ChatMessage;

public record ChatMessageResponse(
        String id,
        String roomId,
        String senderEmail,
        String senderNickname,
        String message,
        int unreadCount,
        String createdAt
) {
    public static ChatMessageResponse fromEntity(ChatMessage entity) {
        return new ChatMessageResponse(
                entity.getId(),
                entity.getRoomId(),
                entity.getSenderEmail(),
                entity.getSenderNickname(),
                entity.getMessage(),
                entity.getUnreadCount(),
                entity.getCreatedAt().toString()
        );
    }
}