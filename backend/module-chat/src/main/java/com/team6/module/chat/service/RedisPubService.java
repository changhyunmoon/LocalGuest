package com.team6.module.chat.service;

import com.team6.module.chat.dto.request.ChatMessageSendRequest;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RedisPubService {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final RedisPublisher redisPublisher;
    private final RedisSubscribeListener redisSubscribeListener;

    // 채널 관리 (이미 구독 중인 채널인지 확인용)
    private final Map<String, ChannelTopic> topics = new ConcurrentHashMap<>();

    public void sendMessage(ChatMessageResponse chatMessageResponse) {
        String roomId = chatMessageResponse.roomId();

        // 해당 방의 토픽이 없으면 생성 및 리스너 등록 (구독)
        ChannelTopic topic = topics.computeIfAbsent(roomId, id -> {
            ChannelTopic newTopic = new ChannelTopic(id);
            redisMessageListenerContainer.addMessageListener(redisSubscribeListener, newTopic);
            return newTopic;
        });

        // 메시지 발행
        redisPublisher.publish(topic, chatMessageResponse);
    }
}