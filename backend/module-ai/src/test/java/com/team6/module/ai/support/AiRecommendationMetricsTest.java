package com.team6.module.ai.support;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRecommendationMetricsTest {

    private SimpleMeterRegistry registry;
    private AiRecommendationMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiRecommendationMetrics(registry);
    }

    @Test
    void counters_should_increment() {
        metrics.recordPromptRecommendCall();
        metrics.recordNoRegionShortCircuit();
        metrics.recordRegionExpansion();
        metrics.recordSparsePoolNotice();
        metrics.recordFallback("DROP_REGION");

        assertThat(registry.counter("localguest.ai.recommend.prompt_calls").count()).isEqualTo(1);
        assertThat(registry.counter("localguest.ai.recommend.no_region_short_circuit").count()).isEqualTo(1);
        assertThat(registry.counter("localguest.ai.recommend.region_expansion").count()).isEqualTo(1);
        assertThat(registry.counter("localguest.ai.recommend.sparse_pool_notice").count()).isEqualTo(1);
        assertThat(registry.counter("localguest.ai.recommend.fallback", "stage", "DROP_REGION").count()).isEqualTo(1);
    }
}
