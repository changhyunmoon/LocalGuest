package com.team6.module.chat.service;

import com.team6.module.chat.support.ChatPresenceRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

/**
 * 채팅 STOMP presence용 Redis 키 정리.
 * MySQL 참가자 제거·방 삭제와 동기화해 오래된 세트/세션 키가 남지 않게 한다.
 */
@Slf4j
@Service
public class ChatPresenceRedisService {

    private final RedisTemplate<String, String> redisTemplate;

    public ChatPresenceRedisService(
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    /** HTTP 퇴장 등으로 DB에서만 나간 경우, 실시간 세트에서도 이메일을 제거한다. */
    public void removeUserFromRoomParticipants(String roomId, String userEmail) {
        String key = ChatPresenceRedisKeys.roomParticipantsKey(roomId);
        Long removed = redisTemplate.opsForSet().remove(key, userEmail);
        log.info("[CHAT_PRESENCE] Redis SREM roomId={} email={} removedMembers={}", roomId, userEmail, removed);
    }

    /**
     * 방이 완전히 삭제될 때: 참가자 세트 삭제 + 해당 방을 가리키는 USER_SESSION 해시 키 삭제.
     */
    public void purgeRoomPresence(String roomId) {
        String setKey = ChatPresenceRedisKeys.roomParticipantsKey(roomId);
        Boolean setDeleted = redisTemplate.delete(setKey);
        log.info("[CHAT_PRESENCE] Redis DEL room participants key={}, existed={}", setKey, Boolean.TRUE.equals(setDeleted));

        int sessionKeysRemoved = deleteUserSessionsForRoom(roomId);
        log.info("[CHAT_PRESENCE] Redis USER_SESSION purge for roomId={} removedKeys={}", roomId, sessionKeysRemoved);
    }

    private int deleteUserSessionsForRoom(String roomId) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(ChatPresenceRedisKeys.USER_SESSION_PREFIX + "*")
                .count(200)
                .build();
        int removed = 0;
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Object ridObj = redisTemplate.opsForHash().get(key, "roomId");
                String rid = ridObj != null ? ridObj.toString() : null;
                if (roomId.equals(rid)) {
                    redisTemplate.delete(key);
                    removed++;
                }
            }
        }
        return removed;
    }
}
