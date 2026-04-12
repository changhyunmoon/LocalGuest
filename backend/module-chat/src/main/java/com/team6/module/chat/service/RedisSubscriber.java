package com.team6.module.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String publishMessage = (String) redisTemplate.getValueSerializer().deserialize(message.getBody());

            ChatMessageResponse roomMessage = objectMapper.readValue(publishMessage, ChatMessageResponse.class);

            messagingTemplate.convertAndSend("/sub/chat/room/" + roomMessage.roomId(), roomMessage);

            log.info("Redis Subscribed message sent to: /sub/chat/room/{}", roomMessage.roomId());

        } catch (Exception e) {
            log.error("Redis Subscribe Error: {}", e.getMessage());
        }
    }
}