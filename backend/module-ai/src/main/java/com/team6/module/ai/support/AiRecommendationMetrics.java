package com.team6.module.ai.support;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 프롬프트 추천 파이프라인 관측용 Micrometer 카운터. 이름은 {@code localguest.ai.recommend.*} 접두사를 쓴다.
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
}
