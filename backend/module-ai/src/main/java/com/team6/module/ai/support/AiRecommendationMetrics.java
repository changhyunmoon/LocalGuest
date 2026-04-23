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
 *   <li>{@code .fallback(stage=...)} — 조건 완화 재시도({@code DROP_ACTIVITY_TAGS_ONLY} 등 또는 {@code STRATEGIC_EXHAUSTED})</li>
 *   <li>{@code .strategic_fallback_outcome(result=adopted|exhausted,policy_version=...)} — 전략 완화 체인 실행 시 최종 채택 여부(대시보드에서 adopted/(adopted+exhausted))</li>
 *   <li>{@code .outcome(type=no_region|empty|success)} — 최종 건수 결과</li>
 *   <li>{@code .feedback_penalty_hits(policy_version=...)} — 후보 1명 스코어링 시
 *       {@link com.team6.module.ai.policy.FeedbackMatchPolicy} 감점이 적용된 횟수(0보다 작은 기여)</li>
 *   <li>{@code .exposure(policy_version=...,rank=...)} — 추천 카드 노출(랭크별)</li>
 *   <li>{@code .debiased_click_used(policy_version=...,used=true|false)} — 디바이어스 클릭 신호 사용 여부(가이드별)</li>
 *   <li>{@code .negative_filter(reason=region|style|language,policy_version=...)} — 부정 의도로 제외된 후보 수</li>
 *   <li>{@code .budget_range_match(result=match|miss,policy_version=...)} — 범위 예산 매칭 분기(비교 가능 후보에 한함)</li>
 *   <li>{@code .llm_prompt_extraction(result=success|empty|error,policy_version=...,llm_provider=openai|gemini|unknown)} — LLM 프롬프트 추출 시도 결과</li>
 * </ul>
 * <b>Timer / Summary</b> (태그 {@code policy_version} 권장)
 * <ul>
 *   <li>{@code .latency} — end-to-end 지연</li>
 *   <li>{@code .top1_score} — Top1 룰 점수</li>
 *   <li>{@code .effective_pool_size} — 확장 후 후보 풀 크기</li>
 *   <li>{@code .feedback_penalty_magnitude} — 후보별 피드백 감점 절댓값(룰 점수에서 깎인 양, 단위: 점)</li>
 *   <li>{@code .diversity_penalty_magnitude} — Top-N에서 2번째 슬롯부터 선택 시 적용된 유사도×λ 패널티(점수 스케일)</li>
 *   <li>{@code .llm_prompt_extraction_latency} — LLM 추출 호출 지연(카운터와 동일 태그: {@code policy_version}, {@code result}, {@code llm_provider})</li>
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
     * 후보 풀/후보 필터링 단계에서 제외 사유를 집계한다.
     * 예: 결제 완료 일정 충돌, 운영 제외 ID, 희소 풀 등.
     *
     * @param reason schedule_conflict / config_excluded / sparse_pool 등(소문자 권장)
     * @param removedCount 제외된 후보 수(0이면 기록하지 않음)
     */
    public void recordCandidateExclusion(String reason, int removedCount, String policyVersion) {
        if (removedCount <= 0) {
            return;
        }
        String pv = policyVersion == null ? "unknown" : policyVersion;
        String r = reason == null || reason.isBlank() ? "unknown" : reason;
        registry.counter(PREFIX + ".candidate_exclusion",
                Tags.of("policy_version", pv, "reason", r)).increment(removedCount);
    }

    /**
     * @param relaxStage 조건 완화 단계 이름(예: {@code DROP_ACTIVITY_TAGS_ONLY}). 체인 실패 시 {@code STRATEGIC_EXHAUSTED}.
     */
    public void recordFallback(String relaxStage) {
        String stage = relaxStage == null || relaxStage.isBlank() ? "NONE" : relaxStage;
        registry.counter(PREFIX + ".fallback", "stage", stage).increment();
    }

    /**
     * 전략적 조건 완화 체인을 실제로 돌린 뒤, 완화된 추천을 채택했는지(adopted) 소진했는지(exhausted).
     * Prometheus 등에서 {@code sum(rate(...{result="adopted"})) / sum(rate(...{result=~"adopted|exhausted"}))} 로 채택률 근사.
     */
    public void recordStrategicFallbackOutcome(boolean adopted, String policyVersion) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        String result = adopted ? "adopted" : "exhausted";
        registry.counter(PREFIX + ".strategic_fallback_outcome",
                Tags.of("policy_version", pv, "result", result)).increment();
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

    /**
     * {@link com.team6.module.ai.engine.ScoreCalculator}에서 후보별로 기록.
     * 피드백(환불·저평점) 룰이 총점에 음수 기여를 한 경우에만 호출한다.
     *
     * @param penaltyMagnitude 양수(예: 피드백 정책 점수가 -16이면 16)
     */
    public void recordFeedbackPenalty(int penaltyMagnitude, String policyVersion) {
        if (penaltyMagnitude <= 0) {
            return;
        }
        String pv = policyVersion == null ? "unknown" : policyVersion;
        Tags tags = Tags.of("policy_version", pv);
        registry.counter(PREFIX + ".feedback_penalty_hits", tags).increment();
        registry.summary(PREFIX + ".feedback_penalty_magnitude", tags).record(penaltyMagnitude);
    }

    /**
     * 다양성 랭킹에서 이미 고른 가이드와의 유사도×λ로 깎인 점수(양수). Top1(첫 슬롯)에는 적용되지 않으므로 기록하지 않는다.
     */
    public void recordDiversityPenaltyMagnitude(double penaltyPoints, String policyVersion) {
        if (penaltyPoints < 0.0) {
            return;
        }
        String pv = policyVersion == null ? "unknown" : policyVersion;
        registry.summary(PREFIX + ".diversity_penalty_magnitude", Tags.of("policy_version", pv))
                .record(penaltyPoints);
    }

    /**
     * 추천 카드 클릭 이벤트(관심/탐색 신호).
     *
     * @param rank 추천 리스트 내 노출 순서(1부터). 미전달/비정상이면 unknown.
     */
    public void recordRecommendationClick(Integer rank, String policyVersion) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        String r = (rank == null || rank <= 0) ? "unknown" : String.valueOf(rank);
        registry.counter(PREFIX + ".click", Tags.of("policy_version", pv, "rank", r)).increment();
    }

    /**
     * 추천 카드 노출(랭크별).
     */
    public void recordRecommendationExposure(Integer rank, String policyVersion) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        String r = (rank == null || rank <= 0) ? "unknown" : String.valueOf(rank);
        registry.counter(PREFIX + ".exposure", Tags.of("policy_version", pv, "rank", r)).increment();
    }

    /**
     * 디바이어스 클릭 신호(포지션 바이어스 보정)가 사용됐는지.
     */
    public void recordDebiasedClickUsed(boolean used, String policyVersion) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        registry.counter(PREFIX + ".debiased_click_used", Tags.of("policy_version", pv, "used", String.valueOf(used)))
                .increment();
    }

    /**
     * 부정 의도로 필터링된 후보 수(상세 원인 태그).
     */
    public void recordNegativeFilter(String reason, int removedCount, String policyVersion) {
        if (removedCount <= 0) {
            return;
        }
        String pv = policyVersion == null ? "unknown" : policyVersion;
        String r = reason == null || reason.isBlank() ? "unknown" : reason;
        registry.counter(PREFIX + ".negative_filter", Tags.of("policy_version", pv, "reason", r))
                .increment(removedCount);
    }

    /**
     * 범위 예산 매칭 분기(후보/선호 모두 범위를 가진 경우).
     */
    public void recordBudgetRangeMatch(boolean matched, String policyVersion) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        String result = matched ? "match" : "miss";
        registry.counter(PREFIX + ".budget_range_match", Tags.of("policy_version", pv, "result", result))
                .increment();
    }

    /**
     * {@link com.team6.module.ai.spi.LlmPromptExtractor} 호출 1회당 기록한다.
     *
     * @param result {@code success}(비어 있지 않은 요청), {@code empty}(Optional.empty로 룰 파서 폴백),
     *               {@code error}(예외 후 룰 파서 폴백)
     * @param nanos  LLM 호출 구간 소요 시간
     * @param llmProvider {@code localguest.ai.llm-provider}에 대응하는 태그 값({@code openai}, {@code gemini}, 그 외는 {@code unknown})
     */
    public void recordLlmPromptExtraction(String result, long nanos, String policyVersion, String llmProvider) {
        String pv = policyVersion == null ? "unknown" : policyVersion;
        String r = result == null || result.isBlank() ? "unknown" : result;
        String prov = sanitizeLlmProviderTag(llmProvider);
        Tags tags = Tags.of("policy_version", pv, "result", r, "llm_provider", prov);
        registry.counter(PREFIX + ".llm_prompt_extraction", tags).increment();
        registry.timer(PREFIX + ".llm_prompt_extraction_latency", tags).record(Math.max(0L, nanos), TimeUnit.NANOSECONDS);
    }

    private static String sanitizeLlmProviderTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String s = raw.trim().toLowerCase();
        if ("openai".equals(s)) {
            return "openai";
        }
        if ("gemini".equals(s)) {
            return "gemini";
        }
        return "unknown";
    }
}
