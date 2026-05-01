package com.team6.module.chat.dto.request;

import java.io.Serializable;

public record ChatMessageSendRequest(
        String roomId,
        String message
) implements Serializable {
    private static final long serialVersionUID = 1L;
}