package com.team6.module.ai.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 프롬프트 추천 파이프라인 관측용 Micrometer. 이름 접두사: {@code localguest.ai.recommend.*}
 * <p>
 * <b>Counter</b>
 * <ul>
 *   <li>{@code .prompt_calls} — {@link com.team6.module.ai.service.PromptRecommendationService#recommendByPrompt} 진입</li>
 *   <li>{@code .no_region_short_circuit} — 지역 미입력 단축</li>
 *   <li>{@code .region_expansion} — 인접 지역 후보 확장 사용</li>
 *   <li>{@code .sparse_pool_notice} — 희소 풀 안내 부착</li>
 *   <li>{@code .fallback(stage=...)} — 조건 완화 재시도</li>
 *   <li>{@code .outcome(type=no_region|empty|success)} — 최종 건수 결과</li>
 * </ul>
 * <b>Timer / Summary</b> (태그 {@code policy_version} 권장)
 * <ul>
 *   <li>{@code .latency} — end-to-end 지연</li>
 *   <li>{@code .top1_score} — Top1 룰 점수</li>
 *   <li>{@code .effective_pool_size} — 확장 후 후보 풀 크기</li>
 * </ul>
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
     * 프롬프트 추천 최종 비즈니스 결과. {@code no_region}: 지역 미입력 단축,
     * {@code empty}: 추천 0건, {@code success}: 1건 이상.
     */
    public void recordOutcome(String outcomeType) {
        String t = outcomeType == null || outcomeType.isBlank() ? "unknown" : outcomeType;
        registry.counter(PREFIX + ".outcome", "type", t).increment();
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
