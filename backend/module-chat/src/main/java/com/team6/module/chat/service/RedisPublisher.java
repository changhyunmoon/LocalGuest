package com.team6.module.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisPublisher {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ChannelTopic topic, ChatMessageResponse message) {
        try {
            // 💡 객체를 JSON 문자열로 변환하여 전송
            String jsonMessage = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(topic.getTopic(), jsonMessage);
        } catch (JsonProcessingException e) {
            // 로깅 처리
            throw new RuntimeException("메시지 직렬화 실패", e);
        }
    }
}
