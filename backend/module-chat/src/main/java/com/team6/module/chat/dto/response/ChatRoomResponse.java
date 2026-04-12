package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mysql.ChatRoom;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatRoomResponse(
        String roomId,          // 비즈니스 UUID
        String title,           // 방 제목
        String lastMessage,     // 마지막 메시지 내용
        LocalDateTime lastMessageAt, // 마지막 메시지 시간
        Integer participantCount,    // 참여 인원수
        String ownerEmail,      // 방장 이메일
        LocalDateTime createdAt      // 생성일
) {

    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return ChatRoomResponse.builder()
                .roomId(chatRoom.getRoomId())
                .title(chatRoom.getTitle())
                .lastMessage(chatRoom.getLastMessage())
                .lastMessageAt(chatRoom.getLastMessageAt())
                .participantCount(chatRoom.getParticipantCount())
                .ownerEmail(chatRoom.getOwnerEmail()) // 엔티티의 변경된 필드 반영
                .createdAt(chatRoom.getCreatedAt())
                .build();
    }
}