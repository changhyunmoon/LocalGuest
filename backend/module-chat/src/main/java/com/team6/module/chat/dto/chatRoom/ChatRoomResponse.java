package com.team6.module.chat.dto.chatRoom;

import com.team6.module.chat.entity.mysql.ChatRoom;
import java.time.LocalDateTime;

public record ChatRoomResponse(
        String roomId,
        String title,
        String ownerEmail,
        int participantCount,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long unreadCount,
        LocalDateTime createdAt
) {
    /**
     * 방 생성 직후 혹은 단순 정보 조회 시 (unreadCount가 굳이 필요 없을 때)
     */
    public static ChatRoomResponse from(ChatRoom entity) {
        return new ChatRoomResponse(
                entity.getRoomId(),
                entity.getTitle(),
                entity.getOwnerEmail(),
                entity.getParticipantCount(),
                entity.getLastMessage(),
                entity.getLastMessageAt(),
                0L, // 기본값
                entity.getCreatedAt()
        );
    }

    /**
     * 채팅방 목록 조회 시 (안 읽은 메시지 수 포함)
     */
    public static ChatRoomResponse of(ChatRoom entity, long unreadCount) {
        return new ChatRoomResponse(
                entity.getRoomId(),
                entity.getTitle(),
                entity.getOwnerEmail(),
                entity.getParticipantCount(),
                entity.getLastMessage(),
                entity.getLastMessageAt(),
                unreadCount,
                entity.getCreatedAt()
        );
    }
}