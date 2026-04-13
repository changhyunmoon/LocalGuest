package com.team6.module.chat.service;

import com.team6.module.chat.dto.chatMessage.ChatMessageRequest;
import com.team6.module.chat.dto.chatMessage.ChatMessageResponse;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.MongoTemplate; // 추가
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final MongoTemplate mongoTemplate; // MongoRepository 외에 벌크 업데이트를 위해 필요
    private final ChatRoomService chatRoomService;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    /**
     * 메시지 전송 및 저장
     */
    @Transactional
    public void sendMessage(ChatMessageRequest request) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(request.roomId())
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다: " + request.roomId()));

        int totalParticipants = chatRoom.getParticipantCount();

        // Redis 접속자 확인 (키 형식 주의)
        String redisKey = "CHAT_ROOM_PARTICIPANTS:" + request.roomId();
        Long activeCount = redisTemplate.opsForSet().size(redisKey);
        int currentActive = (activeCount != null) ? activeCount.intValue() : 0;

        // 안 읽은 인원 = 전체 인원 - 현재 접속 인원
        int unreadCount = Math.max(0, totalParticipants - currentActive);

        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(request.roomId())
                .senderEmail(request.senderEmail())
                .senderNickname(request.senderNickname())
                .message(request.message())
                .unreadCount(unreadCount)
                .build();

        chatMessageRepository.save(chatMessage);

        chatRoom.updateLastMessage(request.message(), chatMessage.getCreatedAt());

        SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/sub/chat/room/" + request.roomId(), ChatMessageResponse.from(chatMessage));
        }
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
     * 채팅방 입장 시 읽음 처리
     */
    @Transactional
    public void markAsRead(String roomId, String userEmail) {
        // 1. MongoDB 벌크 업데이트 (상대방 메시지 전체 읽음)
        Query query = new Query(Criteria.where("roomId").is(roomId)
                .and("senderEmail").ne(userEmail)
                .and("unreadCount").gt(0));
        Update update = new Update().set("unreadCount", 0);
        mongoTemplate.updateMulti(query, update, ChatMessage.class);

        // 2. MySQL 시간 업데이트 (리스트 페이지 카운트 동기화)
        chatRoomService.updateLastReadAt(roomId, userEmail);

        // 3. 웹소켓 실시간 알림 (이미 방에 있는 사람들에게 숫자 갱신 알림)
        SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
        if (messagingTemplate != null) {
            Map<String, Object> readUpdate = new HashMap<>();
            readUpdate.put("type", "READ_UPDATE");
            readUpdate.put("roomId", roomId);
            readUpdate.put("userEmail", userEmail); // 기존 userEmail 키값 유지

            messagingTemplate.convertAndSend("/sub/chat/room/" + roomId, readUpdate);
        }

        log.info("[MarkAsRead] Room: {}, User: {} - DB & List Sync Completed", roomId, userEmail);
    }
}