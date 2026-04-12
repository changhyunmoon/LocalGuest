package com.team6.module.chat.service;

import com.team6.module.chat.dto.request.ChatMessageRequest;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import com.team6.module.chat.dto.response.ChatScrollResponse;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ChatMessageRepository chatMessageRepository; // MongoDB
    private final ChatRoomRepository chatRoomRepository;       // MySQL
    private final SimpMessagingTemplate messagingTemplate;      // 알림 전송용 추가

    private static final String ROOM_PARTICIPANTS = "CHAT_ROOM_PARTICIPANTS:";
    private static final int PAGE_SIZE = 20;

    @Transactional
    public ChatMessageResponse processSendMessage(ChatMessageRequest request) {
        String roomId = request.roomId();

        // 1. 실시간 unreadCount 계산
        int unreadCount = calculateUnreadCount(roomId);

        // 2. MongoDB 엔티티 생성 및 저장
        ChatMessage messageEntity = ChatMessage.create(
                roomId,
                request.senderEmail(),
                request.senderNickname(),
                request.message(),
                unreadCount
        );
        chatMessageRepository.save(messageEntity);

        // 3. MySQL ChatRoom 정보 업데이트 (마지막 메시지, 시간)
        updateChatRoomStatus(roomId, request.message(), messageEntity.getCreatedAt());

        ChatMessageResponse response = ChatMessageResponse.fromEntity(messageEntity);

        // 4. [추가] 실시간 리스트 갱신 알림 전송
        // 채팅방 목록 페이지에 있는 참여자들에게 새 메시지 발생을 알림
        notifyChatListUpdate(roomId, response);

        return response;
    }

    /**
     * 방 참여자 전원에게 리스트 갱신 신호를 보냄
     */
    private void notifyChatListUpdate(String roomId, ChatMessageResponse response) {
        ChatRoom room = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        // 방에 속한 모든 참여자에게 본인의 이메일을 토픽으로 알림 전송
        room.getParticipants().forEach(participant -> {
            String topic = "/sub/chat/list/" + participant.getUserEmail();
            messagingTemplate.convertAndSend(topic, response);
            log.info("[List Update] Sent to: {}, Room: {}", topic, roomId);
        });
    }

    public ChatScrollResponse getMessagesBefore(String roomId, String lastMessageId) {

        Pageable pageable = PageRequest.of(0, PAGE_SIZE + 1); // 하나 더 가져와서 hasNext 확인

        List<ChatMessage> entities;

        if (lastMessageId == null || lastMessageId.isEmpty()) {
            // 첫 페이지 조회
            entities = chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageable);
        } else {
            // 이전 데이터 조회 (String을 ObjectId로 변환 필수)
            entities = chatMessageRepository.findBeforeId(roomId, new org.bson.types.ObjectId(lastMessageId), pageable);
        }

        boolean hasNext = entities.size() > PAGE_SIZE;
        List<ChatMessageResponse> messages = entities.stream()
                .limit(PAGE_SIZE)
                .map(ChatMessageResponse::fromEntity)
                .toList();

        return ChatScrollResponse.of(messages, hasNext);
    }

    private int calculateUnreadCount(String roomId) {
        Long connectedCount = redisTemplate.opsForSet().size(ROOM_PARTICIPANTS + roomId);

        ChatRoom room = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        int total = room.getParticipantCount();
        return Math.max(0, total - (connectedCount != null ? connectedCount.intValue() : 0)); // 1대신 0으로 보정 (본인 포함 여부에 따라 조정 가능)
    }

    private void updateChatRoomStatus(String roomId, String lastMsg, LocalDateTime sentAt) {
        chatRoomRepository.findByRoomId(roomId).ifPresent(room -> {
            room.updateLastMessage(lastMsg, sentAt);
        });
    }
}