package com.team6.module.chat.service;

import com.team6.module.chat.config.RedisPublisher;
import com.team6.module.chat.dto.request.SendMessageRequest;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;


    //안 읽은 메시지 개수 조회
    public Long countUnreadMessages(String roomId, LocalDateTime lastReadAt) {
        // lastReadAt이 null인 경우 (방에 처음 초대된 상태)
        if (lastReadAt == null) {
            // 시스템에서 다룰 수 있는 현실적인 최소 날짜를 사용합니다. (오버플로우 방지)
            // 혹은 0을 리턴하거나 전체 메시지를 카운트하도록 비즈니스 로직에 따라 결정합니다.
            LocalDateTime startOfTime = LocalDateTime.of(2026, 1, 1, 0, 0);
            return chatMessageRepository.countByRoomIdAndCreatedAtAfter(roomId, startOfTime);
        }

        return chatMessageRepository.countByRoomIdAndCreatedAtAfter(roomId, lastReadAt);
    }

    public ChatMessage saveMessage(SendMessageRequest request, String senderNickname) {
        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(request.roomId())
                .senderId(request.senderId())
                .senderNickname(senderNickname)
                .message(request.message())
                .build();

        return chatMessageRepository.save(chatMessage);
    }
}