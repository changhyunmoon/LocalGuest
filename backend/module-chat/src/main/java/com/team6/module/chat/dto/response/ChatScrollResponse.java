package com.team6.module.chat.dto.response;

import java.util.List;

public record ChatScrollResponse(
        List<ChatMessageResponse> messages,
        String lastMessageId,
        boolean hasNext
) {
    public static ChatScrollResponse of(List<ChatMessageResponse> messages, boolean hasNext) {
        String lastId = messages.isEmpty() ? null : messages.get(messages.size() - 1).id();
        return new ChatScrollResponse(messages, lastId, hasNext);
    }
}