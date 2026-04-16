package com.team6.module.ai.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 추천 카드 클릭 이벤트를 in-memory로 보관한다(1인스턴스 기준).
 * <p>
 * MVP(2단계)는 DB/스트림이 없으므로 단기(최근 N일) 신호만 반영한다.
 */
@Component
public class AiRecommendClickStore {

    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

    private final Map<Long, Deque<Long>> clicksByGuideId = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration window;

    public AiRecommendClickStore() {
        this(Clock.systemUTC(), DEFAULT_WINDOW);
    }

    AiRecommendClickStore(Clock clock, Duration window) {
        this.clock = clock;
        this.window = window == null ? DEFAULT_WINDOW : window;
    }

    public void recordClick(Long guideId) {
        if (guideId == null) {
            return;
        }
        long now = clock.millis();
        Deque<Long> q = clicksByGuideId.computeIfAbsent(guideId, ignored -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(now);
            pruneLocked(q, now);
        }
    }

    public int recentClickCount(Long guideId) {
        if (guideId == null) {
            return 0;
        }
        Deque<Long> q = clicksByGuideId.get(guideId);
        if (q == null) {
            return 0;
        }
        long now = clock.millis();
        synchronized (q) {
            pruneLocked(q, now);
            return q.size();
        }
    }

    private void pruneLocked(Deque<Long> q, long nowEpochMs) {
        long cutoff = nowEpochMs - window.toMillis();
        while (!q.isEmpty()) {
            Long ts = q.peekFirst();
            if (ts == null || ts >= cutoff) {
                break;
            }
            q.removeFirst();
        }
    }
}

