package com.team6.module.chat.dto.response;

import java.io.Serializable;
import java.time.LocalDateTime;

public record ChatMessageResponse(
        String id,
        String roomId,
        String senderEmail,
        String senderNickname,
        String message,
        LocalDateTime createdAt
) implements Serializable {
}