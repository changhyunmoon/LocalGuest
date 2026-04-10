package com.team6.module.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatRoomPresenceService {
    //서버 간에 공유되는 '중앙 세션 저장소' 역할을 합니다. 누가 어느 방에 있는지 Redis에 기록합니다.

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String PRESENCE_KEY_PREFIX = "room:presence:";

    // 유저가 방에 입장할 때 호출 (Key: room:roomId:userId, Value: "online")
    public void enterRoom(String roomId, Long userId) {
        String key = PRESENCE_KEY_PREFIX + roomId + ":" + userId;
        redisTemplate.opsForValue().set(key, "online", Duration.ofMinutes(30));
    }

    // 유저가 방을 나갈 때 호출
    public void exitRoom(String roomId, Long userId) {
        String key = PRESENCE_KEY_PREFIX + roomId + ":" + userId;
        redisTemplate.delete(key);
    }

    // 특정 유저가 현재 이 방에 접속 중인지 확인
    public int countOnlineUsers(String roomId, List<Long> userIds) {
        List<String> keys = userIds.stream()
                .map(id -> PRESENCE_KEY_PREFIX + roomId + ":" + id)
                .toList();

        // Redis multiGet으로 한 번에 조회
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) return 0;

        return (int) values.stream().filter(Objects::nonNull).count();
    }

}
