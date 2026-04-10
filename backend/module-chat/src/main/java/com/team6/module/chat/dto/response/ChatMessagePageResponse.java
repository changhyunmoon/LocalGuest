package com.team6.module.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatMessagePageResponse {
    private List<ChatMessageResponse> messages;
    private String nextCursor; // 마지막으로 읽은 메시지의 ID
    private boolean hasNext;   // 다음 페이지 존재 여부

    public static ChatMessagePageResponse of(List<ChatMessageResponse> messages, boolean hasNext) {
        String nextCursor = hasNext && !messages.isEmpty()
                ? messages.get(messages.size() - 1).getMessageId()
                : null;

        return ChatMessagePageResponse.builder()
                .messages(messages)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }
}


