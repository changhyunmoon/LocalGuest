package com.team6.module.ai.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 세션 단위로 "최근 추천 노출"을 가볍게 집계하는 in-memory store.
 * <p>
 * - 영속화/클러스터 공유 없음(단일 인스턴스 기준) — MVP용.
 * - 쿨다운/반복 추천 완화 신호로만 사용.
 */
@Component
public class AiRecommendExposureStore {

    private static final Duration WINDOW = Duration.ofMinutes(30);
    private static final int MAX_EVENTS_PER_SESSION = 50;

    private final Map<String, Deque<Event>> bySession = new ConcurrentHashMap<>();

    @Autowired(required = false)
    @Qualifier("memberRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    public void recordExposure(String sessionId, Long guideId) {
        if (sessionId == null || sessionId.isBlank() || guideId == null) {
            return;
        }

        if (redisTemplate != null) {
            String key = redisKey(sessionId, guideId);
            Long v = redisTemplate.opsForValue().increment(key);
            if (v != null && v == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            return;
        }

        Deque<Event> deque = bySession.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (deque) {
            pruneLocked(deque, nowMillis());
            if (deque.size() >= MAX_EVENTS_PER_SESSION) {
                deque.pollFirst();
            }
            deque.addLast(new Event(guideId, nowMillis()));
        }
    }

    public int recentExposureCount(String sessionId, Long guideId) {
        if (sessionId == null || sessionId.isBlank() || guideId == null) {
            return 0;
        }

        if (redisTemplate != null) {
            String v = redisTemplate.opsForValue().get(redisKey(sessionId, guideId));
            if (v == null || v.isBlank()) {
                return 0;
            }
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        Deque<Event> deque = bySession.get(sessionId);
        if (deque == null) {
            return 0;
        }
        long now = nowMillis();
        synchronized (deque) {
            pruneLocked(deque, now);
            int count = 0;
            for (Event e : deque) {
                if (guideId.equals(e.guideId())) {
                    count++;
                }
            }
            return count;
        }
    }

    private static void pruneLocked(Deque<Event> deque, long nowMillis) {
        long cutoff = nowMillis - WINDOW.toMillis();
        while (!deque.isEmpty()) {
            Event first = deque.peekFirst();
            if (first == null || first.atMillis() >= cutoff) {
                break;
            }
            deque.pollFirst();
        }
    }

    private static long nowMillis() {
        return System.currentTimeMillis();
    }

    private static String redisKey(String sessionId, Long guideId) {
        return "ai:session:expo:" + sessionId + ":guide:" + guideId;
    }

    private record Event(Long guideId, long atMillis) {
    }
}

