package com.team6.module.chat.config;

import com.team6.module.chat.dto.request.ChatMessageRequest;
import com.team6.module.chat.service.ChatMessageService;
import com.team6.module.chat.service.ChatRoomPresenceService;
import com.team6.module.chat.service.RedisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final ChatRoomPresenceService presenceService;
    private final ChatMessageService chatMessageService;
    private final RedisPublisher redisPublisher;

    // 1. 입장 처리: 유저가 특정 채팅방 토픽을 '구독'할 때
    @EventListener
    public void handleSubscriptionListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String roomId = (String) headerAccessor.getSessionAttributes().get("roomId");
        String userIdStr = (String) headerAccessor.getSessionAttributes().get("userId");

        if (roomId != null && userIdStr != null) {
            Long userId = Long.parseLong(userIdStr);
            presenceService.enterRoom(roomId, userId);
            chatMessageService.decreaseUnreadCount(roomId, userId);

            // 개선 1: 읽음 이벤트 발행 (Redis Pub/Sub)
            ChatMessageRequest readEvent = new ChatMessageRequest();
            readEvent.setRoomId(roomId);
            readEvent.setSenderId(userId);
            readEvent.setType("READ"); // 타입 지정

            redisPublisher.publish(new ChannelTopic("chat"), readEvent);
        }
    }

    // 2. 퇴장 처리: 웹소켓 연결이 끊길 때 (브라우저 종료, 블루-그린 서버 교체 등)
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String roomId = (String) headerAccessor.getSessionAttributes().get("roomId");
        String userIdStr = (String) headerAccessor.getSessionAttributes().get("userId");

        if (roomId != null && userIdStr != null) {
            Long userId = Long.parseLong(userIdStr);

            // Redis에서 접속 상태 제거
            presenceService.exitRoom(roomId, userId);

            log.info("[퇴장] 유저 {} 번이 방 {} 에서 나갔습니다.", userId, roomId);
        }
    }


}