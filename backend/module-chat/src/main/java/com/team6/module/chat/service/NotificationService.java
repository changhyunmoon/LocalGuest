package com.team6.module.chat.service;

import com.team6.module.chat.dto.notification.ChatNotificationResponse;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    @Qualifier("chatRedisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatRoomRepository chatRoomRepository;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // [1] SSE 구독 시작
    public SseEmitter subscribe(String userEmail) {
        SseEmitter emitter = new SseEmitter(60 * 1000L * 60); // 1시간
        emitters.put(userEmail, emitter);

        emitter.onCompletion(() -> emitters.remove(userEmail));
        emitter.onTimeout(() -> emitters.remove(userEmail));

        // 연결 직후 더미 데이터 전송 (503 에러 방지)
        sendToLocalEmitter(userEmail, ChatNotificationResponse.of("CONNECT", null, null, userEmail, null));
        return emitter;
    }

    // [2] Redis 채널로 알림 발행 (서버 간 통신 시작점)
    public void broadcast(ChatNotificationResponse response) {

        redisTemplate.convertAndSend("chat-notifications", response);
    }

    // [3] Redis 메시지 수신 후 내 서버에 연결된 유저에게 전송 (최적화 로직)
    public void processNotification(ChatNotificationResponse res) {
        if ("NEW_MESSAGE".equals(res.type())) {
            // findByRoomId 대신 'WithParticipants'가 붙은 메서드 호출!
            chatRoomRepository.findByRoomIdWithParticipants(res.roomId()).ifPresent(room -> {
                room.getParticipants().forEach(participant -> {
                    String email = participant.getUserEmail();
                    if (!email.equals(res.senderEmail()) && emitters.containsKey(email)) {
                        sendToLocalEmitter(email, res);
                    }
                });
            });
        } else {
            // 초대(NEW_ROOM) 등 특정 수신자가 명시된 경우
            if (res.receiverEmail() != null && emitters.containsKey(res.receiverEmail())) {
                sendToLocalEmitter(res.receiverEmail(), res);
            }
        }
    }

    // [4] 실제 SSE 전송
    private void sendToLocalEmitter(String userEmail, Object data) {
        SseEmitter emitter = emitters.get(userEmail);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("chat-event").data(data));
            } catch (IOException e) {
                emitters.remove(userEmail);
            }
        }
    }
}