package com.team6.module.chat.service;

import com.team6.module.chat.dto.request.ChatMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisPublisher {
    private final RedisTemplate<String, Object> redisTemplate;

    public void publish(ChannelTopic topic, ChatMessageRequest message) {
        redisTemplate.convertAndSend(topic.getTopic(), message);
    }
}
