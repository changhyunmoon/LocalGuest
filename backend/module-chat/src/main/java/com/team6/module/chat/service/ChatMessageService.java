package com.team6.module.chat.service;

import com.team6.module.chat.dto.request.ChatMessageRequest;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomPresenceService presenceService;
    private final ChatRoomService chatRoomService;
    private final MongoTemplate mongoTemplate;

    /**
     * 메시지 저장 및 unreadCount 계산
     */
    @Transactional
    public ChatMessageResponse saveMessage(ChatMessageRequest request) {
        ChatRoom room = chatRoomService.getRoomEntity(request.getRoomId());

        // 개선 4: 참여자 ID 리스트를 뽑아서 한 번에 Redis 조회 (N+1 해결)
        List<Long> participantIds = room.getParticipants().stream()
                .map(ChatParticipant::getUserId)
                .toList();

        int onlineCount = presenceService.countOnlineUsers(room.getRoomId(), participantIds);
        int unreadCount = Math.max(0, room.getParticipantCount() - onlineCount);

        // 3. 메시지 생성 및 MongoDB 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(request.getRoomId())
                .senderId(request.getSenderId())
                .senderNickname(request.getSenderNickname())
                .message(request.getMessage())
                .unreadCount(unreadCount)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        // 4. MySQL 방 정보 업데이트
        chatRoomService.updateLastMessage(
                request.getRoomId(),
                request.getMessage(),
                savedMessage.getCreatedAt()
        );

        return ChatMessageResponse.from(savedMessage);
    }

    /**
     * 방 입장 시 읽음 처리 (Bulk Update)
     */
    @Transactional
    public void decreaseUnreadCount(String roomId, Long myId) {
        // 내가 읽지 않은(senderId != myId) 다른 사람들의 메시지 중 숫자가 남은 것들
        Query query = new Query(
                Criteria.where("roomId").is(roomId)
                        .and("senderId").ne(myId)
                        .and("unreadCount").gt(0)
        );

        Update update = new Update().inc("unreadCount", -1);

        // 몽고DB 대량 업데이트 실행
        mongoTemplate.updateMulti(query, update, ChatMessage.class);
        log.info("방 {} 에서 유저 {} 가 메시지 읽음 처리를 수행했습니다.", roomId, myId);
    }
}