package com.team6.module.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessagePageResponse(
        List<ChatMessageResponse> messages,
        LocalDateTime nextCursor,
        boolean hasNext
) {
}