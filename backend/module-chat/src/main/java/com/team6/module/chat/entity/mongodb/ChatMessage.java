package com.team6.module.chat.entity.mongodb;


import com.team6.module.common.global.entity.BaseTimeEntity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Document(collection = "chat_messages")
@CompoundIndex(def = "{'roomId': 1, 'createdAt': -1}")
public class ChatMessage {

    @Id
    private String id; // MongoDB 전용 ObjectId

    private String roomId; // MySQL ChatRoom의 roomId와 일치함

    private String senderEmail; // 메시지 보낸 사람의 PK

    private String senderNickname; // 보낸 사람 닉네임

    private String message; // 실제 대화 내용

    private LocalDateTime createdAt; // 메시지 전송 시간
//
//    private int unreadCount; //이 메시지를 읽지 않은 사람 수 (초기값: 전체 참여자 수 - 1)

    private MessageType type;

    @Builder
    private ChatMessage(String roomId, String senderEmail, String senderNickname, String message, MessageType type) {
        this.roomId = roomId;
        this.senderEmail = senderEmail;
        this.senderNickname = senderNickname;
        this.message = message;
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }


}