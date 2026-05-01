package com.team6.module.chat.entity.mysql;

import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatParticipant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String userEmail;// 참여 유저의 PK

    private String userNickname; // 채팅 시 노출될 닉네임 캐싱

    private LocalDateTime lastReadAt; // 마지막으로 읽은 시점

    @Builder
    private ChatParticipant(String userEmail, String userNickname) {
        this.userEmail = userEmail;
        this.userNickname = userNickname;
        this.lastReadAt = LocalDateTime.now();
    }

    public void updateLastReadAt() {
        this.lastReadAt = LocalDateTime.now();
    }




}