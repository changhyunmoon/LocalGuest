package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mongodb.ChatMessage;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatMessageResponse(
        String id,
        String roomId,
        Long senderId,
        String senderNickname,
        String message,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage entity) {
        return ChatMessageResponse.builder()
                .id(entity.getId())
                .roomId(entity.getRoomId())
                .senderId(entity.getSenderId())
                .senderNickname(entity.getSenderNickname())
                .message(entity.getMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}