package com.team6.module.chat.service;

import com.team6.module.chat.config.RedisPublisher;
import com.team6.module.chat.dto.request.ChatMessageRequest;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final RedisPublisher redisPublisher;

    //메시지 전송 및 저장
    @Transactional
    public void sendMessage(ChatMessageRequest request) {
        log.info("채팅 메시지 전송 시작 - roomId: {}, senderId: {}", request.roomId(), request.senderId());

        // 엔티티 생성 및 MongoDB 저장
        ChatMessage chatMessage = ChatMessage.create(
                request.roomId(),
                request.senderId(),
                request.senderNickname(),
                request.message()
        );

        // MongoDB에 저장 (성공 시 자동 생성된 ID가 채워짐)
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        log.info("MongoDB 메시지 저장 완료 - id: {}", savedMessage.getId());

        // Redis 발행을 위한 Response DTO 변환
        ChatMessageResponse response = ChatMessageResponse.from(savedMessage);

        // Redis 토픽(chatroom)으로 메시지 발행
        redisPublisher.publish(response);
        log.info("Redis 메시지 발행 완료 - roomId: {}", response.roomId());
    }
}