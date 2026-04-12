package com.team6.module.chat.config;


import com.team6.module.chat.service.ChatRoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class PresenceInterceptor implements ChannelInterceptor {

    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate; // @Lazy 주입 예정
    private final ChatRoomService chatRoomService;

    // Redis Key 규칙
    private static final String ROOM_PARTICIPANTS = "CHAT_ROOM_PARTICIPANTS:";
    private static final String USER_SESSION = "USER_SESSION:";

    // 💡 생성자를 직접 작성하고 SimpMessagingTemplate에 @Lazy를 붙여 순환 고리를 끊습니다.
    public PresenceInterceptor(
            RedisTemplate<String, String> redisTemplate,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            ChatRoomService chatRoomService) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.chatRoomService = chatRoomService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        String userEmail = (String) accessor.getSessionAttributes().get("userEmail");
        String sessionId = accessor.getSessionId();
        StompCommand command = accessor.getCommand();

        if (StompCommand.SUBSCRIBE.equals(command)) {
            String roomId = getRoomId(accessor.getDestination());
            if (roomId != null && userEmail != null) {
                redisTemplate.opsForSet().add(ROOM_PARTICIPANTS + roomId, userEmail);

                String sessionKey = USER_SESSION + sessionId;
                redisTemplate.opsForHash().put(sessionKey, "userEmail", userEmail);
                redisTemplate.opsForHash().put(sessionKey, "roomId", roomId);

                chatRoomService.updateLastReadTime(roomId, userEmail);
                sendReadUpdate(roomId, userEmail);

                log.info("[Presence] 입성 - 유저: {}, 방: {}", userEmail, roomId);
            }
        } else if (StompCommand.DISCONNECT.equals(command)) {
            String sessionKey = USER_SESSION + sessionId;
            String savedEmail = (String) redisTemplate.opsForHash().get(sessionKey, "userEmail");
            String savedRoomId = (String) redisTemplate.opsForHash().get(sessionKey, "roomId");

            if (savedEmail != null && savedRoomId != null) {
                redisTemplate.opsForSet().remove(ROOM_PARTICIPANTS + savedRoomId, savedEmail);
                redisTemplate.delete(sessionKey);
                log.info("[Presence] 퇴장 - 유저: {}, 방: {}", savedEmail, savedRoomId);
            }
        }
        return message;
    }

    private void sendReadUpdate(String roomId, String userEmail) {
        Map<String, String> alert = new HashMap<>();
        alert.put("type", "READ_UPDATE");
        alert.put("userEmail", userEmail);
        messagingTemplate.convertAndSend("/sub/chat/room/" + roomId, alert);
    }

    private String getRoomId(String destination) {
        if (destination == null || !destination.startsWith("/sub/chat/room/")) return null;
        return destination.substring(destination.lastIndexOf("/") + 1);
    }
}