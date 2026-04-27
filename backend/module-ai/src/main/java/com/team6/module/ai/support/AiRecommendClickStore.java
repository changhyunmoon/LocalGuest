package com.team6.module.ai.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 추천 카드 클릭 이벤트를 in-memory로 보관한다(1인스턴스 기준).
 * <p>
 * MVP(2단계)는 DB/스트림이 없으므로 단기(최근 N일) 신호만 반영한다.
 */
@Component
public class AiRecommendClickStore {

    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);
    private static final int MAX_RANK = 10;
    private static final Duration RANK_STATS_TTL = Duration.ofDays(30);

    private final Map<Long, Deque<Long>> clicksByGuideId = new ConcurrentHashMap<>();
    private final Map<Long, Map<Integer, Long>> clicksByGuideRank = new ConcurrentHashMap<>();
    private final Map<Long, Map<Integer, Long>> exposuresByGuideRank = new ConcurrentHashMap<>();
    private final Map<Integer, Long> globalClicksByRank = new ConcurrentHashMap<>();
    private final Map<Integer, Long> globalExposuresByRank = new ConcurrentHashMap<>();

    private final Clock clock;
    private final Duration window;

    @Autowired(required = false)
    @Qualifier("memberRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    public AiRecommendClickStore() {
        this(Clock.systemUTC(), DEFAULT_WINDOW);
    }

    AiRecommendClickStore(Clock clock, Duration window) {
        this.clock = clock;
        this.window = window == null ? DEFAULT_WINDOW : window;
    }

    public void recordClick(Long guideId) {
        recordClick(guideId, null);
    }

    public void recordClick(Long guideId, Integer rank) {
        if (guideId == null) {
            return;
        }

        if (redisTemplate != null) {
            recordRecentClickRedis(guideId);
            recordRankClickRedis(guideId, rank);
            return;
        }

        recordRecentClickInMemory(guideId);
        recordRankClickInMemory(guideId, rank);
    }

    public int recentClickCount(Long guideId) {
        if (guideId == null) {
            return 0;
        }

        if (redisTemplate != null) {
            return recentClickCountRedis(guideId);
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

    /**
     * 추천 결과가 사용자에게 "노출"되었다고 기록한다(랭크 포함).
     * <p>
     * - 세션 반복 완화는 별도 {@link AiRecommendExposureStore}가 담당한다.
     * - 이 메서드는 포지션 바이어스 보정(랭크별 노출/클릭 통계)에만 사용한다.
     */
    public void recordExposure(Long guideId, Integer rank) {
        if (guideId == null) {
            return;
        }
        if (redisTemplate != null) {
            recordRankExposureRedis(guideId, rank);
            return;
        }
        recordRankExposureInMemory(guideId, rank);
    }

    /**
     * 포지션(랭크) 편향을 완화한 클릭 신호 점수.
     * <p>
     * 글로벌 랭크별 CTR을 이용해, 하위 랭크에서 발생한 클릭을 상대적으로 더 큰 신호로 본다(상한/완충 포함).
     */
    public int debiasedClickScore(Long guideId) {
        if (guideId == null) {
            return 0;
        }
        if (redisTemplate != null) {
            return debiasedClickScoreRedis(guideId);
        }
        return debiasedClickScoreInMemory(guideId);
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

    private void recordRecentClickInMemory(Long guideId) {
        long now = clock.millis();
        Deque<Long> q = clicksByGuideId.computeIfAbsent(guideId, ignored -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(now);
            pruneLocked(q, now);
        }
    }

    private void recordRankClickInMemory(Long guideId, Integer rank) {
        int r = normalizeRank(rank);
        clicksByGuideRank.computeIfAbsent(guideId, ignored -> new ConcurrentHashMap<>())
                .merge(r, 1L, Long::sum);
        globalClicksByRank.merge(r, 1L, Long::sum);
    }

    private void recordRankExposureInMemory(Long guideId, Integer rank) {
        int r = normalizeRank(rank);
        exposuresByGuideRank.computeIfAbsent(guideId, ignored -> new ConcurrentHashMap<>())
                .merge(r, 1L, Long::sum);
        globalExposuresByRank.merge(r, 1L, Long::sum);
    }

    private int debiasedClickScoreInMemory(Long guideId) {
        Map<Integer, Long> guideClicks = clicksByGuideRank.getOrDefault(guideId, Map.of());
        if (guideClicks.isEmpty()) {
            return 0;
        }
        double ctr1 = ctr(globalClicksByRank.getOrDefault(1, 0L), globalExposuresByRank.getOrDefault(1, 0L));
        if (ctr1 <= 0.0) {
            ctr1 = 0.05;
        }
        double sum = 0.0;
        for (int r = 1; r <= MAX_RANK; r++) {
            long c = guideClicks.getOrDefault(r, 0L);
            if (c <= 0) continue;
            double ctrR = ctr(globalClicksByRank.getOrDefault(r, 0L), globalExposuresByRank.getOrDefault(r, 0L));
            double w = ctrR <= 0.0 ? 1.0 : Math.min(5.0, ctr1 / ctrR);
            sum += c * w;
        }
        return (int) Math.round(Math.min(50.0, sum));
    }

    private void recordRecentClickRedis(Long guideId) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();
        String key = "ai:click:z:" + guideId;
        redisTemplate.opsForZSet().add(key, now + "-" + UUID.randomUUID(), now);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
        redisTemplate.expire(key, window.plus(Duration.ofDays(1)));
    }

    private int recentClickCountRedis(Long guideId) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();
        String key = "ai:click:z:" + guideId;
        Long count = redisTemplate.opsForZSet().count(key, cutoff, now);
        return count == null ? 0 : count.intValue();
    }

    private void recordRankClickRedis(Long guideId, Integer rank) {
        int r = normalizeRank(rank);
        incrWithTtl("ai:click:rank:" + r, RANK_STATS_TTL);
        incrWithTtl("ai:click:guide:" + guideId + ":rank:" + r, RANK_STATS_TTL);
    }

    private void recordRankExposureRedis(Long guideId, Integer rank) {
        int r = normalizeRank(rank);
        incrWithTtl("ai:expo:rank:" + r, RANK_STATS_TTL);
        incrWithTtl("ai:expo:guide:" + guideId + ":rank:" + r, RANK_STATS_TTL);
    }

    private int debiasedClickScoreRedis(Long guideId) {
        double ctr1 = ctr(readLong("ai:click:rank:1"), readLong("ai:expo:rank:1"));
        if (ctr1 <= 0.0) {
            ctr1 = 0.05;
        }
        double sum = 0.0;
        for (int r = 1; r <= MAX_RANK; r++) {
            long c = readLong("ai:click:guide:" + guideId + ":rank:" + r);
            if (c <= 0) continue;
            double ctrR = ctr(readLong("ai:click:rank:" + r), readLong("ai:expo:rank:" + r));
            double w = ctrR <= 0.0 ? 1.0 : Math.min(5.0, ctr1 / ctrR);
            sum += c * w;
        }
        return (int) Math.round(Math.min(50.0, sum));
    }

    private void incrWithTtl(String key, Duration ttl) {
        Long v = redisTemplate.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redisTemplate.expire(key, ttl);
        }
    }

    private long readLong(String key) {
        String v = redisTemplate.opsForValue().get(key);
        if (v == null || v.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static double ctr(long clicks, long exposures) {
        if (exposures <= 0) {
            return 0.0;
        }
        return (double) clicks / (double) exposures;
    }

    private static int normalizeRank(Integer rank) {
        if (rank == null || rank <= 0) {
            return 0;
        }
        return Math.min(MAX_RANK, rank);
    }
}

