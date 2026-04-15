package com.team6.module.chat.service;

import com.mongodb.client.result.UpdateResult;
import com.team6.module.chat.dto.chatMessage.ChatMessageRequest;
import com.team6.module.chat.dto.chatMessage.ChatMessageResponse;
import com.team6.module.chat.dto.notification.ChatNotificationResponse;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.entity.mongodb.MessageType;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final NotificationService notificationService;

    /**
     * 메시지 전송 및 저장
     */
    @Transactional
    public void sendMessage(ChatMessageRequest request) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(request.roomId())
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + request.roomId()));

        String activeUsersKey = "CHAT_ROOM_PARTICIPANTS:" + request.roomId();
        Long activeCount = redisTemplate.opsForSet().size(activeUsersKey);
        int currentActive = (activeCount != null) ? activeCount.intValue() : 0;

        int totalParticipants = chatRoom.getParticipantCount();
        int unreadCount = Math.max(0, totalParticipants - currentActive);

        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(request.roomId())
                .senderEmail(request.senderEmail())
                .senderNickname(request.senderNickname())
                .message(request.message())
                .unreadCount(unreadCount)
                .type(MessageType.TALK)
                .build();

        chatMessageRepository.save(chatMessage);
        chatRoom.updateLastMessage(request.message(), chatMessage.getCreatedAt());

        SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/sub/chat/room/" + request.roomId(), ChatMessageResponse.from(chatMessage));
        }

        notificationService.broadcast(ChatNotificationResponse.of(
                "NEW_MESSAGE", request.roomId(), request.senderEmail(), null, null
        ));
    }

    /**
     * 과거 메시지 조회
     */
    @Transactional(readOnly = true)
    public Slice<ChatMessageResponse> getChatMessages(String roomId, Pageable pageable) {
        Slice<ChatMessage> messageSlice = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
        return messageSlice.map(ChatMessageResponse::from);
    }

    /**
     * 채팅방 입장 시 읽음 처리 (REST API 호출용)
     */
    @Transactional
    public void markAsRead(String roomId, String userEmail) {
        // 1. 참여자 정보 조회
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + roomId));

        ChatParticipant participant = findParticipant(chatRoom, userEmail);

        // 2. [핵심] 이전 읽은 시점 보관 (gt 이슈 방지를 위해 1초 여유 권장)
        LocalDateTime previousReadAt = participant.getLastReadAt().minusSeconds(1);

        // 3. MongoDB: 벌크 업데이트 (내가 나간 사이의 메시지들 unreadCount - 1)
        Query query = new Query(Criteria.where("roomId").is(roomId)
                .and("createdAt").gte(previousReadAt) // 1초 전 시점부터 포함하여 안전하게 처리
                .and("senderEmail").ne(userEmail)
                .and("unreadCount").gt(0));

        UpdateResult result = mongoTemplate.updateMulti(query, new Update().inc("unreadCount", -1), ChatMessage.class);
        log.info("[Read Process] Batch updated {} messages for user {}", result.getModifiedCount(), userEmail);

        // 4. MySQL: 마지막 읽은 시점 현재로 갱신 (더티 체킹)
        participant.updateLastReadAt();

        // 5. WebSocket: 다른 참여자들에게 읽음 이벤트 알림 전송
        sendReadUpdate(roomId, userEmail);
    }

    /**
     * 참여자 리스트에서 특정 유저 찾기 (Helper)
     */
    private ChatParticipant findParticipant(ChatRoom chatRoom, String userEmail) {
        return chatRoom.getParticipants().stream()
                .filter(p -> p.getUserEmail().equals(userEmail))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("참여자가 아닙니다: " + userEmail));
    }

    /**
     * 실시간 읽음 상태 업데이트 알림 전송 (Helper)
     */
    private void sendReadUpdate(String roomId, String userEmail) {
        Map<String, String> payload = new HashMap<>();
        payload.put("type", "READ_UPDATE");
        payload.put("roomId", roomId);
        payload.put("userEmail", userEmail);

        SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/sub/chat/room/" + roomId, payload);
        }
    }
}