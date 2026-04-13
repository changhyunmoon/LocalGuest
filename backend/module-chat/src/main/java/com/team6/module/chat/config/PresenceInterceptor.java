package com.team6.module.chat.config;

import com.team6.module.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider; // 추가
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class PresenceInterceptor implements ChannelInterceptor {

    private final RedisTemplate<String, String> redisTemplate;
    // ObjectProvider를 사용하여 빈 생성 시점이 아닌 사용 시점에 주입받도록 함
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final ChatRoomService chatRoomService;

    private static final String ROOM_PARTICIPANTS = "CHAT_ROOM_PARTICIPANTS:";
    private static final String USER_SESSION = "USER_SESSION:";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        String sessionId = accessor.getSessionId();
        String userEmail = (String) accessor.getSessionAttributes().get("userEmail");

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String roomId = getRoomId(accessor.getDestination());
            if (roomId != null && userEmail != null) {
                redisTemplate.opsForSet().add(ROOM_PARTICIPANTS + roomId, userEmail);
                Map<String, String> sessionData = Map.of("email", userEmail, "roomId", roomId);
                redisTemplate.opsForHash().putAll(USER_SESSION + sessionId, sessionData);

                chatRoomService.updateLastReadAt(roomId, userEmail);
                sendReadUpdate(roomId, userEmail);
                log.info("[Presence] User {} entered room {}", userEmail, roomId);
            }
        }
        else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(USER_SESSION + sessionId);
            if (!sessionData.isEmpty()) {
                String email = (String) sessionData.get("email");
                String roomId = (String) sessionData.get("roomId");

                // 퇴장 시에도 마지막 읽은 시간 업데이트
                chatRoomService.updateLastReadAt(roomId, email);

                redisTemplate.opsForSet().remove(ROOM_PARTICIPANTS + roomId, email);
                redisTemplate.delete(USER_SESSION + sessionId);
                log.info("[Presence] User {} left room {}", email, roomId);
            }
        }
        return message;
    }

    private void sendReadUpdate(String roomId, String userEmail) {
        Map<String, String> payload = Map.of("type", "READ_UPDATE", "userEmail", userEmail);
        // 필요할 때 getIfAvailable()로 꺼내서 사용
        SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/sub/chat/room/" + roomId, payload);
        }
    }

    private String getRoomId(String destination) {
        if (destination == null || !destination.startsWith("/sub/chat/room/")) return null;
        return destination.substring(destination.lastIndexOf("/") + 1);
    }
}