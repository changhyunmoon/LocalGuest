package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mongodb.ChatMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class ChatMessageResponse {
    private String messageId;
    private String roomId;
    private Long senderId;
    private String senderNickname;
    private String message;
    private String createdAt;
    private int unreadCount;

    public static ChatMessageResponse from(ChatMessage entity) {
        return ChatMessageResponse.builder()
                .messageId(entity.getId())
                .roomId(entity.getRoomId())
                .senderId(entity.getSenderId())
                .senderNickname(entity.getSenderNickname())
                .message(entity.getMessage())
                .createdAt(entity.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .unreadCount(entity.getUnreadCount())
                .build();
    }
}