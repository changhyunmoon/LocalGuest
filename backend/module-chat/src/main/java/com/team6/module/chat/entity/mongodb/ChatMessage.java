package com.team6.module.chat.entity.mongodb;

import jakarta.persistence.Id;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Document(collection = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    private String id; // MongoDB 전용 ObjectId

    @Indexed
    private String roomId; // MySQL ChatRoom의 roomId와 일치함

    private Long senderId; // 메시지 보낸 사람의 PK

    private String senderNickname; // 보낸 사람 닉네임

    private String message; // 실제 대화 내용

    private LocalDateTime createdAt; // 메시지 전송 시간

    @Builder
    private ChatMessage(String roomId, Long senderId, String senderNickname, String message) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public static ChatMessage create(String roomId, Long senderId, String senderNickname, String message) {
        return ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .senderNickname(senderNickname)
                .message(message)
                .build();
    }
}