package com.team6.module.chat.entity.mysql;

import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "chat_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String roomId; // 비즈니스 고유 식별자 (UUID)

    @Column(nullable = false)
    private String title; // 단톡방 이름

    private String lastMessage; // 목록용 마지막 메시지 캐시

    private LocalDateTime lastMessageAt; // 정렬용 마지막 메시지 시간

    private Integer participantCount; // 현재 참여 인원수

    private Long ownerId; // 방장 ID

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "chat_room_id")
    private List<ChatParticipant> participants = new ArrayList<>();

    @Builder
    private ChatRoom(String title, Long ownerId) {
        this.roomId = UUID.randomUUID().toString();
        this.title = title;
        this.ownerId = ownerId;
        this.participantCount = 0;
        this.lastMessageAt = LocalDateTime.now();
    }

    public static ChatRoom create(String title, Long ownerId) {
        return ChatRoom.builder()
                .title(title)
                .ownerId(ownerId)
                .build();
    }

    public void addParticipant(ChatParticipant participant) {
        this.participants.add(participant);
        this.participantCount = this.participants.size();
    }

    public void updateLastMessage(String content, LocalDateTime sentAt) {
        this.lastMessage = content;
        this.lastMessageAt = sentAt;
    }


}