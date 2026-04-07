package com.team6.module.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.team6.module.chat.config.RedisPublisher.NotificationMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. 역직렬화: 바이트 배열을 UTF-8 문자열로 변환 (가장 안전한 방법)
            String publishMessage = new String(message.getBody(), StandardCharsets.UTF_8);
            String topic = new String(pattern, StandardCharsets.UTF_8);

            log.info("Redis Topic: {}, Message: {}", topic, publishMessage);

            // 2. 토픽별 분기 처리
            if (topic.equals("chat-messages")) {
                handleChatMessage(publishMessage);
            } else if (topic.equals("user-notifications")) {
                handleNotification(publishMessage);
            }

        } catch (Exception e) {
            log.error("Exception in RedisSubscriber: ", e); // 에러 스택트레이스 전체 출력을 권장합니다.
        }
    }

    /**
     * 일반 채팅 메시지 처리 (브로드캐스팅)
     */
    private void handleChatMessage(String publishMessage) {
        try {
            // 채팅 메시지 구조에 맞는 DTO가 있다면 Object 대신 해당 클래스를 넣으세요.
            Object chatMessage = objectMapper.readValue(publishMessage, Object.class);
            // 예: messagingTemplate.convertAndSend("/sub/chat/" + roomId, chatMessage);
        } catch (Exception e) {
            log.error("Chat message parsing error: {}", e.getMessage());
        }
    }

    /**
     * 개인 알림 처리 (1:1 전용)
     */
    private void handleNotification(String publishMessage) {
        try {
            // JSON 문자열을 NotificationMessage 객체로 변환
            NotificationMessage notification = objectMapper.readValue(publishMessage, NotificationMessage.class);

            log.info("Sending notification to User: {}", notification.targetUserId());

            // WebSocket 전송 (STOMP User Destination)
            // 프론트엔드 구독 경로: /user/queue/notifications
            messagingTemplate.convertAndSendToUser(
                    notification.targetUserId().toString(),
                    "/queue/notifications",
                    notification.data()
            );
        } catch (Exception e) {
            log.error("Notification parsing error: {}", e.getMessage());
        }
    }
}