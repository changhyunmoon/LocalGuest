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

    @Test
    void latency_summary_and_pool_should_record() {
        metrics.recordRecommendationLatencyNanos(1_000_000, "test-policy");
        metrics.recordTop1Score(42, "test-policy");
        metrics.recordEffectivePoolSize(3, "test-policy");

        assertThat(registry.timer("localguest.ai.recommend.latency", "policy_version", "test-policy").count())
                .isEqualTo(1);
        assertThat(registry.summary("localguest.ai.recommend.top1_score", "policy_version", "test-policy").count())
                .isEqualTo(1);
        assertThat(registry.summary("localguest.ai.recommend.effective_pool_size", "policy_version", "test-policy").count())
                .isEqualTo(1);
    }

    @Test
    void feedback_penalty_should_increment_hit_and_magnitude() {
        metrics.recordFeedbackPenalty(16, "test-policy");

        assertThat(registry.counter("localguest.ai.recommend.feedback_penalty_hits", "policy_version", "test-policy")
                .count()).isEqualTo(1);
        assertThat(registry.summary("localguest.ai.recommend.feedback_penalty_magnitude", "policy_version", "test-policy")
                .count()).isEqualTo(1);
    }

    @Test
    void feedback_penalty_zero_should_noop() {
        metrics.recordFeedbackPenalty(0, "test-policy");
        assertThat(registry.counter("localguest.ai.recommend.feedback_penalty_hits", "policy_version", "test-policy")
                .count()).isZero();
    }

    @Test
    void strategic_fallback_outcome_should_tag_adopted_and_exhausted() {
        metrics.recordStrategicFallbackOutcome(true, "pv");
        metrics.recordStrategicFallbackOutcome(false, "pv");

        assertThat(registry.counter("localguest.ai.recommend.strategic_fallback_outcome",
                "policy_version", "pv", "result", "adopted").count()).isEqualTo(1);
        assertThat(registry.counter("localguest.ai.recommend.strategic_fallback_outcome",
                "policy_version", "pv", "result", "exhausted").count()).isEqualTo(1);
    }

    @Test
    void diversity_penalty_magnitude_should_record() {
        metrics.recordDiversityPenaltyMagnitude(12.5, "pv");

        assertThat(registry.summary("localguest.ai.recommend.diversity_penalty_magnitude", "policy_version", "pv").count())
                .isEqualTo(1);
    }

    @Test
    void llm_prompt_extraction_should_record_counter_and_timer() {
        metrics.recordLlmPromptExtraction("success", 500_000, "pv");

        assertThat(registry.counter("localguest.ai.recommend.llm_prompt_extraction",
                "policy_version", "pv", "result", "success").count()).isEqualTo(1);
        assertThat(registry.timer("localguest.ai.recommend.llm_prompt_extraction_latency",
                "policy_version", "pv").count()).isEqualTo(1);
    }
}
