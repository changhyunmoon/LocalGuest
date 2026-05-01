package com.team6.module.chat.entity.mysql;

import com.team6.module.chat.exception.ParticipantNotFoundException;
import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
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
    private String roomId; // 비즈니스 고유 식별자

    @Column(nullable = false)
    private String title; // 단톡방 이름

    private String lastMessage; // 마지막 메시지

    private LocalDateTime lastMessageAt; // 마지막 메시지 시간

    private Integer participantCount; // 현재 참여 인원수

    private String ownerEmail; // 방장 ID

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "chat_room_id")
    private List<ChatParticipant> participants = new ArrayList<>();

    @Builder
    private ChatRoom(String title, String ownerEmail) {
        this.roomId = UUID.randomUUID().toString();
        this.title = title;
        this.ownerEmail = ownerEmail;
        this.participantCount = 0;
        this.lastMessageAt = LocalDateTime.now();
    }

    public void addParticipant(ChatParticipant participant) {
        this.participants.add(participant);
        this.participantCount = this.participants.size();
    }

    public void removeParticipant(String userEmail) {
        boolean removed = this.participants.removeIf(
                participant -> participant.getUserEmail().equals(userEmail)
        );

        if (!removed) {
            throw new ParticipantNotFoundException();
        }

        this.participantCount = this.participants.size();
    }

    public boolean isEmpty() {
        return this.participants.isEmpty();
    }

}