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

    /**
     * 단방향 OneToMany 설정
     * cascade = ALL: 방이 저장/삭제될 때 참여자도 함께 처리
     * orphanRemoval = true: 리스트에서 제거된 참여자는 DB에서도 삭제
     * @JoinColumn: chat_participants 테이블에 생성될 외래키 컬럼명
     */
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

    public void addParticipant(ChatParticipant participant) {
        this.participants.add(participant);
        this.participantCount = this.participants.size();
    }

    public void updateLastMessage(String message) {
        this.lastMessage = message;
        this.lastMessageAt = LocalDateTime.now();
    }

    public void updateParticipantCount(){
        this.participantCount = (this.participants != null) ? this.participants.size() : 0;
    }

}