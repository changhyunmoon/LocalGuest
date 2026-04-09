package com.team6.module.chat.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRoomListResponse {
    private String roomId;
    private String title;
    private Integer participantCount;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;

}