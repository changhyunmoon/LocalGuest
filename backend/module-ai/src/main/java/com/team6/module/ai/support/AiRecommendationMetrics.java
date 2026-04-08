package com.team6.module.ai.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 프롬프트 추천 파이프라인 관측용 Micrometer.
 * <p>
 * Counter: {@code prompt_calls}, {@code no_region_short_circuit}, {@code region_expansion},
 * {@code sparse_pool_notice}, {@code fallback(stage=...)}.
 * Timer/Summary: {@code latency}, {@code top1_score}, {@code effective_pool_size} (태그 {@code policy_version}).
 */
@Component
public class AiRecommendationMetrics {

    private static final String PREFIX = "localguest.ai.recommend";

    private final MeterRegistry registry;

    public AiRecommendationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordPromptRecommendCall() {
        registry.counter(PREFIX + ".prompt_calls").increment();
    }

    public void recordNoRegionShortCircuit() {
        registry.counter(PREFIX + ".no_region_short_circuit").increment();
    }

    public void recordRegionExpansion() {
        registry.counter(PREFIX + ".region_expansion").increment();
    }

    public void recordSparsePoolNotice() {
        registry.counter(PREFIX + ".sparse_pool_notice").increment();
    }

    /**
     * @param relaxStage 조건 완화 단계 이름(예: DROP_REGION). 없으면 NONE.
     */
    public void recordFallback(String relaxStage) {
        String stage = relaxStage == null || relaxStage.isBlank() ? "NONE" : relaxStage;
        registry.counter(PREFIX + ".fallback", "stage", stage).increment();
    }

    /**
     * {@code recommendByPrompt} 전체 소요 시간(나노초).
     */
    public void recordRecommendationLatencyNanos(long nanos, String policyVersion) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        registry.timer(PREFIX + ".latency", Tags.of("policy_version", pv))
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    /** Top1 룰 점수 분포(히스토그램/퍼센타일은 Micrometer 백엔드 설정에 따름). */
    public void recordTop1Score(double score, String policyVersion) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        registry.summary(PREFIX + ".top1_score", Tags.of("policy_version", pv)).record(score);
    }

    /** 지역 필터·인접 확장 적용 후 효과 풀 크기. */
    public void recordEffectivePoolSize(int poolSize, String policyVersion) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        registry.summary(PREFIX + ".effective_pool_size", Tags.of("policy_version", pv))
                .record(Math.max(0, poolSize));
    }
}
