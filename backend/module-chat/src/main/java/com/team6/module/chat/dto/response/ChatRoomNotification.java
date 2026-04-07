package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mysql.ChatRoom;
import lombok.Builder;
import java.time.LocalDateTime;

@Builder
public record ChatRoomNotification(
        String roomId,
        String title,
        Long ownerId,
        Integer participantCount,
        String lastMessage,
        LocalDateTime lastMessageAt,
        String type // 알림 타입 (예: "NEW_ROOM")
) {
    public static ChatRoomNotification from(ChatRoom chatRoom) {
        return ChatRoomNotification.builder()
                .roomId(chatRoom.getRoomId())
                .title(chatRoom.getTitle())
                .ownerId(chatRoom.getOwnerId())
                .participantCount(chatRoom.getParticipantCount())
                .lastMessage(chatRoom.getLastMessage())
                .lastMessageAt(chatRoom.getLastMessageAt())
                .type("NEW_ROOM")
                .build();
    }
}