package com.team6.module.chat.dto.request;

public record SendMessageRequest(
        String roomId,
        Long senderId,
        String message
) {}