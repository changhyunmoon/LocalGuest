package com.team6.module.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.chat.dto.request.ChatMessageSendRequest;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscribeListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Redis에서 받은 바이트 데이터를 객체로 역직렬화
            ChatMessageResponse roomMessage = objectMapper.readValue(message.getBody(), ChatMessageResponse.class);

            // 2. WebSocket 구독자들에게 메시지 전달 (/sub/chat/room/{roomId})
            messagingTemplate.convertAndSend("/sub/chat/room/" + roomMessage.roomId(), roomMessage);

            log.info("Redis Sub 수신 및 WS 전송: RoomId={}, SenderEmail={}", roomMessage.roomId(), roomMessage.senderEmail());
        } catch (Exception e) {
            log.error("메시지 역직렬화 실패: {}", e.getMessage());
        }
    }
}