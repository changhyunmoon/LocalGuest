package com.team6.module.chat.dto.request;

public record ChatMessageRequest(
        String roomId,
        String senderEmail,
        String senderNickname,
        String message
) {}