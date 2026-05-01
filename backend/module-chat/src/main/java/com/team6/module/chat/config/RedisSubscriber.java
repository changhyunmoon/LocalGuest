package com.team6.module.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.chat.dto.notification.ChatNotificationResponse;
import com.team6.module.chat.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("[Redis] Message received from channel: {}", new String(message.getChannel()));
        try {
            ChatNotificationResponse res = objectMapper.readValue(message.getBody(), ChatNotificationResponse.class);
            notificationService.processNotification(res);
        } catch (IOException e) {
            log.error("Redis 메시지 파싱 에러", e);
        }
    }
}
