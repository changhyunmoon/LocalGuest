package com.team6.module.chat.config;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
public class RedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic chatTopic;
    private final ChannelTopic notificationTopic;

    public RedisPublisher(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("chatTopic") ChannelTopic chatTopic,
            @Qualifier("notificationTopic") ChannelTopic notificationTopic) {
        this.redisTemplate = redisTemplate;
        this.chatTopic = chatTopic;
        this.notificationTopic = notificationTopic;
    }

    // 기존 채팅 메시지 발행
    public void publishChat(Object message) {
        redisTemplate.convertAndSend(chatTopic.getTopic(), message);
    }

    // 알림 전송용 발행
    public void publishNotification(Long userId, Object notification) {
        NotificationMessage message = new NotificationMessage(userId, notification);
        redisTemplate.convertAndSend(notificationTopic.getTopic(), message);
    }

    public record NotificationMessage(Long targetUserId, Object data) {}

}