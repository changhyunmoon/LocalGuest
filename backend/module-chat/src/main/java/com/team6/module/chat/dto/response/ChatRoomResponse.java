package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mysql.ChatRoom;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomResponse {
    private String roomId;          // 비즈니스 UUID
    private String title;           // 방 제목
    private String lastMessage;     // 마지막 메시지 내용
    private LocalDateTime lastMessageAt; // 마지막 메시지 시간 (정렬용)
    private Integer participantCount;    // 참여 인원수
    private Long ownerId;           // 방장 ID
    private LocalDateTime createdAt;     // 생성일

    @Builder
    private ChatRoomResponse(String roomId, String title, String lastMessage,
                             LocalDateTime lastMessageAt, Integer participantCount,
                             Long ownerId, LocalDateTime createdAt) {
        this.roomId = roomId;
        this.title = title;
        this.lastMessage = lastMessage;
        this.lastMessageAt = lastMessageAt;
        this.participantCount = participantCount;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }


    //Entity를 Response DTO로 변환
    public static ChatRoomResponse from(ChatRoom chatRoom) {
        return ChatRoomResponse.builder()
                .roomId(chatRoom.getRoomId())
                .title(chatRoom.getTitle())
                .lastMessage(chatRoom.getLastMessage())
                .lastMessageAt(chatRoom.getLastMessageAt())
                .participantCount(chatRoom.getParticipantCount())
                .ownerId(chatRoom.getOwnerId())
                .createdAt(chatRoom.getCreatedAt())
                .build();
    }
}
