package com.team6.module.chat.service;

import com.team6.module.chat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    public void publish(ChannelTopic topic, ChatMessageResponse message) {
        // 해당 토픽(채널)으로 메시지를 발행
        redisTemplate.convertAndSend(topic.getTopic(), message);
    }
}