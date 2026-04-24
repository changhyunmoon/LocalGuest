package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.llm.LlmCopyPiiMasker;
import com.team6.module.ai.llm.LlmGuideRankResult;
import com.team6.module.ai.llm.LlmRankCardComposer;
import com.team6.module.ai.spi.LlmGuideRanker;
import com.team6.module.ai.spi.LlmPromptExtractor;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import com.team6.module.ai.support.ConceptSummaryGenerator;
import com.team6.module.ai.support.RecommendationNoticeCodes;
import com.team6.module.ai.support.RegionCandidateExpansion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.team6.module.ai.dto.response.GuideRecommendItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.team6.module.ai.support.AiRecommendationTuning.POLICY_VERSION;

@Service
@Slf4j
public class PromptRecommendationService {

    // 자연어 프롬프트를 구조화된 추천 요청(region/style/tags/langs...)으로 바꾸는 파서.
    private final PromptParser promptParser;

    // 실제 추천 엔진 진입점.
    // 현재 구현은 AiRecommendationServiceImpl -> MatchingEngine 흐름으로 이어진다.
    private final AiRecommendationService aiRecommendationService;

    // 요청 지역 후보가 너무 적을 때 인접 지역 확장 판단에 사용한다.
    private final AdjacentRegionProvider adjacentRegionProvider;

    // 호출 수, fallback 발생, 지연 시간 같은 운영 관측 지표를 기록한다.
    private final AiRecommendationMetrics metrics;

    // 운영 중 조정 가능한 추천 임계값(low-signal 점수, fallback 개선폭 등) 스냅샷.
    private final ScoringPolicySnapshot scoringPolicy;

    private final LocalGuestAiProperties aiProperties;

    @Nullable
    private final LlmPromptExtractor llmPromptExtractor;

    @Nullable
    private final LlmGuideRanker llmGuideRanker;

    public PromptRecommendationService(
            PromptParser promptParser,
            AiRecommendationService aiRecommendationService,
            AdjacentRegionProvider adjacentRegionProvider,
            AiRecommendationMetrics metrics,
            ScoringPolicySnapshot scoringPolicy,
            LocalGuestAiProperties aiProperties,
            @Autowired(required = false) @Nullable LlmPromptExtractor llmPromptExtractor,
            @Autowired(required = false) @Nullable LlmGuideRanker llmGuideRanker
    ) {
        this.promptParser = promptParser;
        this.aiRecommendationService = aiRecommendationService;
        this.adjacentRegionProvider = adjacentRegionProvider;
        this.metrics = metrics;
        this.scoringPolicy = scoringPolicy;
        this.aiProperties = aiProperties;
        this.llmPromptExtractor = llmPromptExtractor;
        this.llmGuideRanker = llmGuideRanker;
    }

    private static final String NOTICE_REGION_REQUIRED =
            "여행하고 싶은 지역을 알려주시면 더 정확하게 추천할 수 있어요.";
    private static final String NOTICE_ADJACENT_INCLUDED =
            "요청 지역과 가까운 인접 지역 가이드를 포함해 추천했어요.";
    /** API로 넘어온 후보·지역 필터 후 풀에 가이드가 한 명뿐일 때 */
    private static final String NOTICE_SPARSE_GUIDE_POOL =
            "이 조건에 맞는 가이드가 한 분뿐이라 추천 선택 폭이 좁을 수 있어요.";
    private static final String NOTICE_PARSE_LOW =
            "입력이 짧아 해석 여지가 있어요. 예산·일정·활동을 더 적어주시면 정확해져요.";
    private static final String NOTICE_DURATION_VAGUE =
            "일정에 대한 말은 있는데 며칠인지 확정하지 못했어요. ‘2박3일’처럼 적어주시면 좋아요.";

    private static final Pattern DURATION_NIGHTS_HINT = Pattern.compile("\\d{1,2}\\s*박");

    public GuideRecommendResponse recommendByPrompt(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        return recommendByPrompt(prompt, topN, guideCandidates, null);
    }

    /**
     * @param availabilityDayCountByGuideId 희망 기간 내 가용 일수(가이드별). null이면 가용성 랭킹 가산 없음.
     */
    public GuideRecommendResponse recommendByPrompt(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates,
            Map<Long, Integer> availabilityDayCountByGuideId
    ) {
        return recommendByPrompt(prompt, topN, guideCandidates, availabilityDayCountByGuideId, null);
    }

    /**
     * @param availabilityDayCountByGuideId 희망 기간 내 가용 일수(가이드별). null이면 가용성 랭킹 가산 없음.
     * @param availabilityMaxConsecutiveDaysByGuideId 희망 기간 내 최대 연속 가능일(가이드별). null이면 연속 가산 없음.
     */
    public GuideRecommendResponse recommendByPrompt(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates,
            Map<Long, Integer> availabilityDayCountByGuideId,
            Map<Long, Integer> availabilityMaxConsecutiveDaysByGuideId
    ) {
        // PromptRecommendationService는 F03 추천의 "흐름 조정자" 역할을 한다.
        // 여기서는 프롬프트를 파싱하고, 후보군을 보정하고, 추천을 실행하고,
        // 부족한 결과면 fallback을 시도한 뒤 최종 응답(conceptSummary/keywords/notice)을 만든다.
        long startNs = System.nanoTime();
        metrics.recordPromptRecommendCall();

        try {
            // 1) topN 기본값을 보정하고, 자연어 프롬프트를 추천용 구조로 변환한다.
            Integer resolvedTopN = (topN == null || topN <= 0)
                    ? AiRecommendationTuning.DEFAULT_TOP_N
                    : topN;
            ParsedPrompt parsedPrompt = parsePromptToRequest(prompt, resolvedTopN, guideCandidates);
            GuideRecommendRequest parsed = parsedPrompt.request();
            LlmParseTrace llmParseTrace = parsedPrompt.llmTrace();

            // 2) 지역이 없으면 추천 품질이 크게 떨어지기 때문에 여기서 바로 short-circuit 한다.
            // 대신 빈 응답만 보내지 않고 conceptSummary / keywords / matchRequestDraft는 같이 만들어
            // 프론트가 "무엇을 더 입력해야 하는지" 안내할 수 있게 한다.
            if (!notBlank(parsed.getRegion())) {
                GuideRecommendResponse empty = GuideRecommendResponse.builder()
                        .totalCount(0)
                        .recommendations(List.of())
                        .build();
                logRecommendation(
                        prompt,
                        parsed,
                        guideCandidates,
                        empty,
                        empty,
                        null,
                        true,
                        false,
                        poolSize(parsed.getGuideCandidates()),
                        0,
                        llmParseTrace
                );
                metrics.recordNoRegionShortCircuit();
                metrics.recordOutcome("no_region");
                GuideRecommendResponse out = GuideRecommendResponse.builder()
                        .conceptSummary(ConceptSummaryGenerator.generate(parsed))
                        .keywords(keywordsFrom(parsed))
                        .matchRequestDraft(matchRequestDraftFrom(parsed))
                        .notice(NOTICE_REGION_REQUIRED)
                        .noticeCodes(List.of(RecommendationNoticeCodes.REGION_REQUIRED))
                        .promptParseConfidence("LOW")
                        .policyVersion(POLICY_VERSION)
                        .totalCount(0)
                        .recommendations(List.of())
                        .build();
                recordDistributionMetrics(out, 0);
                return out;
            }

            // 3) 정확히 같은 지역의 후보가 부족하면, 인접 지역까지 후보 풀을 넓혀본다.
            // 이 단계는 추천 점수 계산 전 "후보군 보정"에 해당한다.
            RegionCandidateExpansion.Result expansion =
                RegionCandidateExpansion.apply(
                        parsed.getGuideCandidates(),
                        parsed.getRegion(),
                        adjacentRegionProvider::neighbors,
                        Boolean.TRUE.equals(parsed.getAllowAdjacentRegion()),
                        resolvedTopN
                );

        // 4) 실제 점수 계산에 넣을 최종 요청 객체를 다시 만든다.
        // parsed는 파서 원본 결과, effective는 인접 지역 확장까지 반영된 계산용 최종본이라고 보면 된다.
            GuideRecommendRequest effective = GuideRecommendRequest.builder()
                .region(parsed.getRegion())
                .travelStyle(parsed.getTravelStyle())
                .budgetLevel(parsed.getBudgetLevel())
                .budgetMinWon(parsed.getBudgetMinWon())
                .budgetMaxWon(parsed.getBudgetMaxWon())
                .budgetScope(parsed.getBudgetScope())
                .companionType(parsed.getCompanionType())
                .activityTags(parsed.getActivityTags())
                .requiredActivityTags(parsed.getRequiredActivityTags())
                .niceToHaveActivityTags(parsed.getNiceToHaveActivityTags())
                .preferredLanguages(parsed.getPreferredLanguages())
                .headcount(parsed.getHeadcount())
                .durationDays(parsed.getDurationDays())
                .excludedActivityTags(parsed.getExcludedActivityTags())
                .excludedRegions(parsed.getExcludedRegions())
                .excludedTravelStyles(parsed.getExcludedTravelStyles())
                .excludedLanguages(parsed.getExcludedLanguages())
                .softPenaltyActivityTags(parsed.getSoftPenaltyActivityTags())
                .topN(parsed.getTopN())
                .guideCandidates(expansion.candidates())
                .availabilityDayCountByGuideId(availabilityDayCountByGuideId)
                .availabilityMaxConsecutiveDaysByGuideId(availabilityMaxConsecutiveDaysByGuideId)
                .build();

            // 5) 1차 추천을 수행한다.
            // 이 시점의 base는 조건을 그대로 적용했을 때의 첫 추천 결과다.
            GuideRecommendResponse base = aiRecommendationService.recommend(effective);

            // 6) 결과가 없거나 점수가 너무 약하면 활동 태그 -> 스타일 -> 지역 순으로
            // 한 단계씩만 완화하는 전략적 fallback을 시도한다.
            FallbackOutcome fallback = resolveFallbackWithStrategicRelaxation(effective, base);
            GuideRecommendResponse finalBase =
                    applyLlmGuideRankIfEnabled(
                            prompt,
                            fallback.requestUsedForRecommend(),
                            resolvedTopN,
                            fallback.responseAfterFallback()
                    );

            // 7) 파싱 결과가 얼마나 풍부한지(HIGH/MEDIUM/LOW), 어떤 모호함이 있었는지 계산한다.
            // 이 값들은 추천 점수 자체보다 notice/로그/운영 튜닝에 더 가깝게 쓰인다.
            String parseConfidence = computePromptParseConfidence(parsed);
            List<String> ambiguityCodes = collectAmbiguityNoticeCodes(prompt, parsed);
            List<String> parserHints = parsed.getParserNoticeCodes() == null ? List.of() : parsed.getParserNoticeCodes();
            logParserTuningSignals(prompt, parsed, parseConfidence, ambiguityCodes, parserHints);

            // 8) 사용자에게 보여줄 notice 문구를 조립한다.
            // 인접 지역 확장, 후보 수 부족, 파싱 애매함, fallback 재시도 여부를 합쳐 한 문장으로 만든다.
            String notice = fallback.fallbackNotice();
            if (!expansion.expansionUsed()
                    && expansion.exactCount() == 0
                    && finalBase.getTotalCount() > 0
                    && notBlank(parsed.getRegion())) {
                notice = mergeNotice(
                        "\"" + parsed.getRegion().trim()
                                + "\"에는 등록 가이드가 없어 주변·광역 권역 가이드를 보여드려요.",
                        notice
                );
            }
            if (expansion.expansionUsed()) {
                notice = mergeNotice(NOTICE_ADJACENT_INCLUDED, notice);
                metrics.recordRegionExpansion();
            }
            int effectivePoolSizeForNotice = poolSize(expansion.candidates());
            if (effectivePoolSizeForNotice == 1 && finalBase.getTotalCount() > 0) {
                notice = mergeNotice(NOTICE_SPARSE_GUIDE_POOL, notice);
                metrics.recordSparsePoolNotice();
            }
            notice = enrichNoticeForParseQuality(notice, parseConfidence, ambiguityCodes);

            if (fallback.attemptedRelaxChain()) {
                String metricStage = fallback.winningRelaxStage() != RelaxStage.NONE
                        ? fallback.winningRelaxStage().name()
                        : "STRATEGIC_EXHAUSTED";
                metrics.recordFallback(metricStage);
                boolean adopted = fallback.winningRelaxStage() != RelaxStage.NONE;
                metrics.recordStrategicFallbackOutcome(adopted, POLICY_VERSION);
            }

            // 9) 프론트가 기계적으로 처리할 수 있도록 notice를 코드 목록으로도 만든다.
            List<String> noticeCodes = buildNoticeCodes(
                    fallback,
                    expansion.expansionUsed(),
                    expansion.exactCount(),
                    parsed.getRegion(),
                    effectivePoolSizeForNotice,
                    finalBase.getTotalCount(),
                    parserHints,
                    ambiguityCodes,
                    parseConfidence
            );
            metrics.recordOutcome(finalBase.getTotalCount() > 0 ? "success" : "empty");

            // 10) 운영 디버깅용 구조화 로그를 남긴다.
            logRecommendation(
                    prompt,
                    parsed,
                    guideCandidates,
                    base,
                    finalBase,
                    fallback,
                    false,
                    expansion.expansionUsed(),
                    poolSize(expansion.candidates()),
                    expansion.exactCount(),
                    llmParseTrace
            );

            // 11) 최종 응답을 만든다.
            // conceptSummary/keywords는 설명용, matchRequestDraft는 이후 매칭 요청 생성 단계 재사용용,
            // recommendations는 실제 카드 UI에 쓰이는 추천 결과 목록이다.
            GuideRecommendResponse out = GuideRecommendResponse.builder()
                    .conceptSummary(ConceptSummaryGenerator.generate(parsed))
                    .keywords(keywordsFrom(parsed))
                    .matchRequestDraft(matchRequestDraftFrom(parsed))
                    .notice(notice)
                    .noticeCodes(noticeCodes)
                    .promptParseConfidence(parseConfidence)
                    .policyVersion(POLICY_VERSION)
                    .totalCount(finalBase.getTotalCount())
                    .recommendations(finalBase.getRecommendations())
                    .build();
            recordDistributionMetrics(out, poolSize(expansion.candidates()));
            return out;
        } finally {
            // 성공/실패와 상관없이 전체 추천 파이프라인 소요 시간을 남긴다.
            metrics.recordRecommendationLatencyNanos(System.nanoTime() - startNs, POLICY_VERSION);
        }
    }

    /**
     * 운영 로그에서 파싱 실패/모호함 패턴을 수집하기 위한 신호 로그.
     * 프롬프트 원문은 남기지 않고, {@code promptHash}와 “언급 힌트/추출 결과”만 남긴다.
     */
    private void logParserTuningSignals(
            String prompt,
            GuideRecommendRequest parsed,
            String parseConfidence,
            List<String> ambiguityCodes,
            List<String> parserHints
    ) {
        try {
            boolean hasAmbiguity = ambiguityCodes != null && !ambiguityCodes.isEmpty();
            boolean hasParserHints = parserHints != null && !parserHints.isEmpty();
            boolean lowConfidence = "LOW".equalsIgnoreCase(parseConfidence);

            var signals = promptParser.signals(prompt);
            boolean exclusionIntentButNoExcluded =
                    signals != null
                            && signals.matchedExclusionIntentKeywords() != null
                            && !signals.matchedExclusionIntentKeywords().isEmpty()
                            && (parsed.getExcludedActivityTags() == null || parsed.getExcludedActivityTags().isEmpty());

            if (!(lowConfidence || hasAmbiguity || hasParserHints || exclusionIntentButNoExcluded)) {
                return;
            }

            log.info("[AI_PARSER_TUNE] policyVer={} promptHash={} parseConfidence={} ambiguityCodes={} parserHints={} signals={{exclusionIntents={},budgetHint={},durationHint={}}} extracted={{region={},style={},budget={},durationDays={},tags={},excluded={},langs={},headcount={}}}",
                    POLICY_VERSION,
                    promptHash(prompt),
                    parseConfidence,
                    ambiguityCodes,
                    parserHints,
                    signals == null ? null : signals.matchedExclusionIntentKeywords(),
                    signals != null && signals.hasBudgetHint(),
                    signals != null && signals.hasDurationHint(),
                    parsed.getRegion(),
                    parsed.getTravelStyle(),
                    parsed.getBudgetLevel(),
                    parsed.getDurationDays(),
                    parsed.getActivityTags(),
                    parsed.getExcludedActivityTags(),
                    parsed.getPreferredLanguages(),
                    parsed.getHeadcount()
            );
        } catch (Exception e) {
            log.debug("[AI_PARSER_TUNE] logging skipped: {}", e.toString());
        }
    }

    private static int promptHash(String prompt) {
        return Objects.toString(prompt, "").getBytes(StandardCharsets.UTF_8).length == 0
                ? 0
                : Objects.toString(prompt, "").hashCode();
    }

    private void recordDistributionMetrics(GuideRecommendResponse response, int effectivePoolSize) {
        // 추천 품질을 운영에서 관찰하기 위한 보조 지표.
        // Top1 점수와 실제 계산에 쓴 후보 풀 크기를 함께 기록한다.
        metrics.recordEffectivePoolSize(effectivePoolSize, POLICY_VERSION);
        if (response.getRecommendations() != null && !response.getRecommendations().isEmpty()) {
            metrics.recordTop1Score(response.getRecommendations().get(0).getScore(), POLICY_VERSION);
        }
    }

    private static GuideRecommendResponse.Keywords keywordsFrom(GuideRecommendRequest request) {
        // 파서가 읽어낸 조건을 프론트에 구조화 형태로 다시 노출한다.
        // 디버깅, UI 표시, 이후 매칭 요청 폼 기본값 구성에 함께 쓸 수 있다.
        return GuideRecommendResponse.Keywords.builder()
                .region(request.getRegion())
                .travelStyle(request.getTravelStyle())
                .budgetLevel(request.getBudgetLevel())
                .companionType(request.getCompanionType())
                .headcount(request.getHeadcount())
                .durationDays(request.getDurationDays())
                .activityTags(request.getActivityTags())
                .excludedActivityTags(request.getExcludedActivityTags())
                .softPenaltyActivityTags(request.getSoftPenaltyActivityTags())
                .preferredLanguages(request.getPreferredLanguages())
                .build();
    }

    private static GuideRecommendResponse.MatchRequestDraft matchRequestDraftFrom(GuideRecommendRequest request) {
        // AI 추천 응답을 바로 매칭 요청 생성 화면으로 연결하기 위한 초안 데이터다.
        // matching 모듈을 직접 호출하는 건 아니고, 프론트가 이 draft를 읽어 요청 폼 기본값으로 쓴다.
        Integer desiredBudget = null;
        Integer min = request.getBudgetMinWon();
        Integer max = request.getBudgetMaxWon();
        if (min != null && max != null && min >= 0 && max >= 0) {
            long sum = (long) min + (long) max;
            desiredBudget = (int) Math.round(sum / 2.0d);
        }
        return GuideRecommendResponse.MatchRequestDraft.builder()
                .destination(request.getRegion())
                .concept(ConceptSummaryGenerator.generateMatchRequestConcept(request))
                .conceptSummary(ConceptSummaryGenerator.generate(request))
                .desiredBudget(desiredBudget)
                .budgetMinWon(min)
                .budgetMaxWon(max)
                .budgetHint(request.getBudgetLevel())
                .headcount(request.getHeadcount())
                .durationDays(request.getDurationDays())
                .travelStyle(request.getTravelStyle())
                .companionType(request.getCompanionType())
                .activityTags(request.getActivityTags())
                .excludedActivityTags(request.getExcludedActivityTags())
                .preferredLanguages(request.getPreferredLanguages())
                .build();
    }

    private static int poolSize(List<GuideRecommendRequest.GuideCandidateDto> candidates) {
        return candidates == null ? 0 : candidates.size();
    }

    private static String mergeNotice(String primary, String secondary) {
        if (primary == null || primary.isBlank()) {
            return (secondary == null || secondary.isBlank()) ? null : secondary;
        }
        if (secondary == null || secondary.isBlank()) {
            return primary;
        }
        return primary + " " + secondary;
    }

    private static List<String> buildNoticeCodes(
            FallbackOutcome fallback,
            boolean expansionUsed,
            int expansionExactCount,
            String regionForNotice,
            int effectivePoolSize,
            int resultCount,
            List<String> parserHints,
            List<String> ambiguityCodes,
            String parseConfidence
    ) {
        // 사람이 읽는 notice와 별개로, 프론트가 로직 분기/배지 처리에 쓸 수 있도록 코드 형태도 함께 만든다.
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (fallback.coreNoticeCodes() != null) {
            codes.addAll(fallback.coreNoticeCodes());
        }
        if (fallback.attemptedRelaxChain()) {
            if (fallback.winningRelaxStage() != RelaxStage.NONE) {
                codes.add(noticeCodeForWinningStage(fallback.winningRelaxStage()));
            } else if (fallback.chainExhausted()) {
                codes.add(RecommendationNoticeCodes.FALLBACK_STRATEGIC_EXHAUSTED);
                if (resultCount == 0) {
                    codes.add(RecommendationNoticeCodes.CONDITIONS_TOO_STRICT);
                }
            }
        }
        if (expansionUsed) {
            codes.add(RecommendationNoticeCodes.ADJACENT_REGION_INCLUDED);
        } else if (resultCount > 0
                && expansionExactCount == 0
                && notBlank(regionForNotice)) {
            codes.add(RecommendationNoticeCodes.NO_EXACT_REGION_GUIDES_IN_POOL);
        }
        if (effectivePoolSize == 1 && resultCount > 0) {
            codes.add(RecommendationNoticeCodes.SPARSE_GUIDE_POOL);
        }
        if (parserHints != null) {
            for (String h : parserHints) {
                if (h != null && !h.isBlank()) {
                    codes.add(h);
                }
            }
        }
        if (ambiguityCodes != null) {
            for (String a : ambiguityCodes) {
                if (a != null && !a.isBlank()) {
                    codes.add(a);
                }
            }
        }
        if ("LOW".equals(parseConfidence)) {
            codes.add(RecommendationNoticeCodes.PROMPT_PARSE_CONFIDENCE_LOW);
        }
        return new ArrayList<>(codes);
    }

    private static String enrichNoticeForParseQuality(
            String notice,
            String parseConfidence,
            List<String> ambiguityCodes
    ) {
        // 파싱이 애매했던 경우에는 사용자에게 "기간" 힌트를 notice에 덧붙인다.
        String out = notice;
        if (ambiguityCodes != null && ambiguityCodes.contains(RecommendationNoticeCodes.PROMPT_DURATION_AMBIGUOUS)) {
            out = mergeNotice(out, NOTICE_DURATION_VAGUE);
        }
        if ("LOW".equals(parseConfidence)) {
            out = mergeNotice(out, NOTICE_PARSE_LOW);
        }
        return out;
    }

    private static String normalizeForAmbiguityScan(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        return prompt.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static List<String> collectAmbiguityNoticeCodes(String prompt, GuideRecommendRequest parsed) {
        String p = normalizeForAmbiguityScan(prompt);
        List<String> codes = new ArrayList<>();
        // 예산은 가이드/사용자 간 조율 영역으로 보고, 예산 관련 모호 notice는 노출하지 않는다.
        if (parsed.getDurationDays() == null && durationMentionedAmbiguous(p)) {
            codes.add(RecommendationNoticeCodes.PROMPT_DURATION_AMBIGUOUS);
        }
        return codes;
    }

    private static boolean budgetMentionedAmbiguous(String p) {
        // "가성비"는 예산 규모(낮음/높음)보다 '가격 대비 만족'에 더 가깝다.
        // 따라서 budgetLevel을 확정하지 못했더라도 가성비만 언급된 경우에는 예산 모호 notice를 띄우지 않는다.
        boolean valueOnly = p.contains("가성비")
                || p.contains("합리적")
                || p.contains("가격 대비")
                || p.contains("값 대비");
        if (valueOnly) {
            // 금액/총액/1인당/숫자 같은 "예산 확정 단서"가 없다면 가성비 맥락으로 보고 notice를 띄우지 않는다.
            boolean numericBudgetCue = p.contains("1인당")
                    || p.contains("총액")
                    || p.matches(".*\\d+\\s*만\\s*원.*")
                    || p.matches(".*\\d+\\s*만원.*")
                    || p.matches(".*\\d+\\s*원.*");
            if (!numericBudgetCue) {
                return false;
            }
        }
        return p.contains("예산")
                || p.contains("비용")
                || p.contains("경비")
                || p.contains("가격")
                || p.contains("1인당")
                || p.contains("총액")
                || p.contains("얼마나 들");
    }

    private static boolean durationMentionedAmbiguous(String p) {
        return p.contains("며칠")
                || p.contains("기간")
                || p.contains("일정")
                || p.contains("체류")
                || DURATION_NIGHTS_HINT.matcher(p).find();
    }

    /**
     * 지역은 파싱됐을 때, 그 외 신호가 얼마나 풍부한지에 대한 룰 기반 등급.
     */
    private static String computePromptParseConfidence(GuideRecommendRequest p) {
        // region만 있는 프롬프트와, region/style/budget/tags/langs/...가 풍부한 프롬프트를 구분해
        // 추천 결과 해석 난이도를 HIGH/MEDIUM/LOW로 표시하는 간단 룰이다.
        int dims = 0;
        if (notBlank(p.getRegion())) {
            dims++;
        }
        if (notBlank(p.getTravelStyle())) {
            dims++;
        }
        if (notBlank(p.getBudgetLevel())) {
            dims++;
        }
        if (notBlank(p.getCompanionType())) {
            dims++;
        }
        if (p.getActivityTags() != null && !p.getActivityTags().isEmpty()) {
            dims++;
        }
        if (p.getPreferredLanguages() != null && !p.getPreferredLanguages().isEmpty()) {
            dims++;
        }
        if (p.getHeadcount() != null) {
            dims++;
        }
        if (p.getDurationDays() != null) {
            dims++;
        }
        if (p.getExcludedActivityTags() != null && !p.getExcludedActivityTags().isEmpty()) {
            dims++;
        }
        if (p.getSoftPenaltyActivityTags() != null && !p.getSoftPenaltyActivityTags().isEmpty()) {
            dims++;
        }
        if (dims >= 5) {
            return "HIGH";
        }
        if (dims >= 3) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static String noticeCodeForWinningStage(RelaxStage stage) {
        return switch (stage) {
            case DROP_ACTIVITY_TAGS_ONLY -> RecommendationNoticeCodes.FALLBACK_RELAXED_ACTIVITY_TAGS;
            case DROP_TRAVEL_STYLE_ONLY -> RecommendationNoticeCodes.FALLBACK_RELAXED_TRAVEL_STYLE;
            case DROP_REGION_ONLY -> RecommendationNoticeCodes.FALLBACK_RELAXED_REGION;
            default -> RecommendationNoticeCodes.FALLBACK_STRATEGIC_EXHAUSTED;
        };
    }

    private static int topScore(GuideRecommendResponse resp) {
        if (resp == null || resp.getRecommendations() == null || resp.getRecommendations().isEmpty()) {
            return 0;
        }
        return resp.getRecommendations().get(0).getScore();
    }

    /**
     * 운영 로그용: Top1 추천의 구조화 근거 코드를 짧게 요약(개인정보 없음).
     */
    private static String summarizeTopReasonCodes(GuideRecommendResponse resp) {
        if (resp == null || resp.getRecommendations() == null || resp.getRecommendations().isEmpty()) {
            return "";
        }
        GuideRecommendItem top = resp.getRecommendations().get(0);
        if (top.getReasonCodes() == null || top.getReasonCodes().isEmpty()) {
            return "";
        }
        return top.getReasonCodes().stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("|"));
    }

    private static final List<RelaxStage> STRATEGIC_RELAX_ORDER = List.of(
            RelaxStage.DROP_ACTIVITY_TAGS_ONLY,
            RelaxStage.DROP_TRAVEL_STYLE_ONLY,
            RelaxStage.DROP_REGION_ONLY
    );

    /**
     * 후보·신호가 있으나 결과가 없거나 Top1 점수가 낮을 때,
     * 활동 태그 → 여행 스타일 → 지역 순으로 한 차원씩만 누적 완화하며 재추천한다.
     * 선호 언어·제외/소프트 패널티 태그는 원 요청({@code languageAnchor}) 기준으로 끝까지 유지한다.
     */
    private FallbackOutcome resolveFallbackWithStrategicRelaxation(
            GuideRecommendRequest effective,
            GuideRecommendResponse base
    ) {
        // fallback은 "아무 기준 없이 다 풀어버리는" 방식이 아니라,
        // 정보 손실이 상대적으로 적은 조건부터 차례대로 완화하는 전략을 쓴다.
        int candidateCount = effective.getGuideCandidates() == null ? 0 : effective.getGuideCandidates().size();
        if (candidateCount == 0) {
            return FallbackOutcome.errorNotice(
                    base,
                    "연결 가능한 가이드 후보가 없어 추천을 제공하기 어려워요. 지역을 바꾸거나 나중에 다시 시도해 주세요.",
                    List.of(RecommendationNoticeCodes.NO_GUIDE_CANDIDATES),
                    effective
            );
        }

        if (!hasAnySignal(effective)) {
            return FallbackOutcome.errorNotice(
                    base,
                    "원하는 조건(지역/스타일/예산/활동/언어/기간/인원)을 조금 더 구체적으로 알려주세요.",
                    List.of(RecommendationNoticeCodes.PROMPT_DETAIL_REQUESTED),
                    effective
            );
        }

        boolean noResults = base == null || base.getTotalCount() == 0;
        int score = topScore(base);
        boolean lowScore = !noResults && score < scoringPolicy.lowSignalScoreThreshold();
        if (!noResults && !lowScore) {
            // 결과가 충분하면 fallback 없이 그대로 채택한다.
            return FallbackOutcome.noRelax(base, effective);
        }

        List<String> coreCodes = noResults
                ? List.of(RecommendationNoticeCodes.FALLBACK_RELAXED_NO_MATCH)
                : List.of(RecommendationNoticeCodes.FALLBACK_LOW_SCORE_RELAXED);
        String noticeIfExhausted = noResults
                ? "조건을 순서대로 완화해 다시 찾아봤어요."
                : "조건을 순서대로 완화해 추천을 다시 구성했어요.";

        StrategicChainResult chain = runStrategicRelaxationChain(effective, base, scoringPolicy);
        boolean improved = chain.winningStage() != RelaxStage.NONE;
        GuideRecommendResponse finalResp = improved ? chain.bestResponse() : base;
        GuideRecommendRequest requestUsed = improved ? chain.requestUsedForRecommend() : effective;
        boolean exhausted = !improved;
        String notice = improved
                ? "조건을 완화해 다시 추천했어요."
                : noticeIfExhausted;

        return new FallbackOutcome(
                true,
                finalResp,
                chain.winningStage(),
                exhausted,
                notice,
                coreCodes,
                requestUsed
        );
    }

    private StrategicChainResult runStrategicRelaxationChain(
            GuideRecommendRequest languageAnchor,
            GuideRecommendResponse baseline,
            ScoringPolicySnapshot scoring
    ) {
        // 언어는 사용자가 강하게 원하는 경우가 많아서 끝까지 고정하고,
        // activity -> style -> region 순으로만 하나씩 완화한다.
        GuideRecommendRequest current = languageAnchor;
        for (RelaxStage step : STRATEGIC_RELAX_ORDER) {
            if (!isStrategicRelaxApplicable(current, step)) {
                continue;
            }
            current = applyStrategicRelaxStep(languageAnchor, current, step);
            GuideRecommendResponse retried = aiRecommendationService.recommend(current);
            if (acceptsStrategicRetry(retried, baseline, scoring)) {
                return new StrategicChainResult(retried, step, current);
            }
        }
        return new StrategicChainResult(baseline, RelaxStage.NONE, languageAnchor);
    }

    private static boolean acceptsStrategicRetry(
            GuideRecommendResponse retried,
            GuideRecommendResponse baseline,
            ScoringPolicySnapshot scoring
    ) {
        if (retried == null || retried.getTotalCount() == 0) {
            return false;
        }
        int top = topScore(retried);
        int threshold = scoring.lowSignalScoreThreshold();
        if (top >= threshold) {
            return true;
        }
        int minGain = scoring.fallbackMinImprovementOverBase();
        if (minGain <= 0) {
            return false;
        }
        int baseTop = topScore(baseline);
        if (baseline == null || baseline.getTotalCount() == 0) {
            return top >= threshold;
        }
        return top >= baseTop + minGain;
    }

    private static boolean isStrategicRelaxApplicable(GuideRecommendRequest req, RelaxStage step) {
        return switch (step) {
            case DROP_ACTIVITY_TAGS_ONLY ->
                    req.getActivityTags() != null && !req.getActivityTags().isEmpty();
            case DROP_TRAVEL_STYLE_ONLY -> notBlank(req.getTravelStyle());
            case DROP_REGION_ONLY -> notBlank(req.getRegion());
            default -> false;
        };
    }

    /**
     * {@code languageAnchor}의 선호 언어를 항상 유지하고, {@code current}에서 한 차원만 완화한다.
     */
    private static GuideRecommendRequest applyStrategicRelaxStep(
            GuideRecommendRequest languageAnchor,
            GuideRecommendRequest current,
            RelaxStage step
    ) {
        String region = current.getRegion();
        String style = current.getTravelStyle();
        List<String> tags = current.getActivityTags() == null ? List.of() : current.getActivityTags();

        if (step == RelaxStage.DROP_ACTIVITY_TAGS_ONLY) {
            tags = List.of();
        } else if (step == RelaxStage.DROP_TRAVEL_STYLE_ONLY) {
            style = null;
        } else if (step == RelaxStage.DROP_REGION_ONLY) {
            region = null;
        }

        List<String> pinnedLangs = languageAnchor.getPreferredLanguages() == null
                ? List.of()
                : languageAnchor.getPreferredLanguages();

        List<String> req = step == RelaxStage.DROP_ACTIVITY_TAGS_ONLY
                ? List.of()
                : (current.getRequiredActivityTags() == null ? List.of() : current.getRequiredActivityTags());
        List<String> nice = step == RelaxStage.DROP_ACTIVITY_TAGS_ONLY
                ? List.of()
                : (current.getNiceToHaveActivityTags() == null ? List.of() : current.getNiceToHaveActivityTags());

        return GuideRecommendRequest.builder()
                .region(region)
                .travelStyle(style)
                .budgetLevel(current.getBudgetLevel())
                .companionType(current.getCompanionType())
                .activityTags(tags)
                .requiredActivityTags(req.isEmpty() ? null : req)
                .niceToHaveActivityTags(nice.isEmpty() ? null : nice)
                .preferredLanguages(pinnedLangs)
                .headcount(current.getHeadcount())
                .durationDays(current.getDurationDays())
                .excludedActivityTags(current.getExcludedActivityTags())
                .softPenaltyActivityTags(current.getSoftPenaltyActivityTags())
                .topN(current.getTopN())
                .guideCandidates(current.getGuideCandidates())
                .availabilityDayCountByGuideId(current.getAvailabilityDayCountByGuideId())
                .build();
    }

    private record StrategicChainResult(
            GuideRecommendResponse bestResponse,
            RelaxStage winningStage,
            GuideRecommendRequest requestUsedForRecommend
    ) {
    }

    private static boolean hasAnySignal(GuideRecommendRequest req) {
        if (req == null) return false;
        return notBlank(req.getRegion())
                || notBlank(req.getTravelStyle())
                || notBlank(req.getBudgetLevel())
                || notBlank(req.getCompanionType())
                || (req.getActivityTags() != null && !req.getActivityTags().isEmpty())
                || (req.getPreferredLanguages() != null && !req.getPreferredLanguages().isEmpty())
                || req.getHeadcount() != null
                || req.getDurationDays() != null
                || (req.getExcludedActivityTags() != null && !req.getExcludedActivityTags().isEmpty())
                || (req.getSoftPenaltyActivityTags() != null && !req.getSoftPenaltyActivityTags().isEmpty())
                || (req.getRequiredActivityTags() != null && !req.getRequiredActivityTags().isEmpty())
                || (req.getNiceToHaveActivityTags() != null && !req.getNiceToHaveActivityTags().isEmpty());
    }

    /**
     * LLM rank 품질 보호용: excludedActivityTags가 후보 카드 텍스트/태그에 강하게 드러나는 경우
     * LLM에 넘길 풀에서 우선 제거한다(단, 전부 제거되면 원본 풀을 유지).
     */
    static List<GuideRecommendRequest.GuideCandidateDto> filterPoolByExcludedSignals(
            List<String> excludedActivityTags,
            List<GuideRecommendRequest.GuideCandidateDto> pool
    ) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        if (excludedActivityTags == null || excludedActivityTags.isEmpty()) {
            return pool;
        }
        List<String> needles = excludedActivityTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(s -> s.length() >= 2)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (needles.isEmpty()) {
            return pool;
        }

        List<GuideRecommendRequest.GuideCandidateDto> kept = new ArrayList<>();
        for (GuideRecommendRequest.GuideCandidateDto c : pool) {
            if (c == null) continue;
            if (!violatesExcludedSignals(needles, c)) {
                kept.add(c);
            }
        }
        return kept.isEmpty() ? pool : kept;
    }

    private static boolean violatesExcludedSignals(List<String> needlesLower, GuideRecommendRequest.GuideCandidateDto c) {
        if (needlesLower == null || needlesLower.isEmpty() || c == null) {
            return false;
        }
        String hay = buildCandidateTextHaystackLower(c);
        if (hay.isEmpty()) {
            return false;
        }
        for (String n : needlesLower) {
            if (n == null || n.isBlank()) continue;
            if (hay.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String buildCandidateTextHaystackLower(GuideRecommendRequest.GuideCandidateDto c) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, c.getGuideStyle());
        appendIfPresent(sb, joinOrEmpty(c.getSpecialtyTags()));
        appendIfPresent(sb, c.getLlmKeywordsSnippet());
        appendIfPresent(sb, c.getLlmIntroSnippet());
        if (c.getLlmFeedBodiesNewestFirst() != null) {
            for (String s : c.getLlmFeedBodiesNewestFirst()) {
                appendIfPresent(sb, s);
            }
        }
        appendIfPresent(sb, c.getLlmDefaultCourseSnippet());
        appendIfPresent(sb, c.getLlmCareerSnippet());
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void appendIfPresent(StringBuilder sb, String text) {
        if (sb == null) return;
        if (text == null || text.isBlank()) return;
        sb.append(' ').append(text.strip());
    }

    private static String joinOrEmpty(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    private void logRecommendation(
            String prompt,
            GuideRecommendRequest request,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates,
            GuideRecommendResponse base,
            GuideRecommendResponse finalBase,
            FallbackOutcome fallback,
            boolean noRegionShortCircuit,
            boolean regionExpansionUsed,
            int effectivePoolSize,
            int expansionExactCount,
            LlmParseTrace llmParseTrace
    ) {
        try {
            // 추천 품질 이슈가 생겼을 때 promptHash, 후보 수, fallback 단계, Top1 reason code 등으로
            // 운영 로그에서 빠르게 역추적할 수 있게 남기는 구조화 로그다.
            int promptHash = promptHash(prompt);
            int candidateCount = guideCandidates == null ? 0 : guideCandidates.size();
            int baseTopScore = topScore(base);
            int finalTopScore = topScore(finalBase);
            String top1ReasonCodes = summarizeTopReasonCodes(finalBase);
            Long top1GuideId = topGuideId(finalBase);

            log.info("[AI_RECOMMEND] policyVer={} promptHash={} topN={} candidates={} policy={{noRegionShortCircuit={},regionExpansion={},effectivePool={},expansionExact={}}} keywords={{region={},style={},budget={},companion={},headcount={},durationDays={},tags={},excluded={},softPenalty={},langs={}}} base={{count={},topScore={}}} final={{count={},topScore={},top1GuideId={},top1ReasonCodes={}}} fallback={{used={},stage={}}} llmParse={{cfgProvider={},extractorBean={},outcome={}}}",
                    AiRecommendationTuning.POLICY_VERSION,
                    promptHash,
                    request == null ? null : request.getTopN(),
                    candidateCount,
                    noRegionShortCircuit,
                    regionExpansionUsed,
                    effectivePoolSize,
                    expansionExactCount,
                    request == null ? null : request.getRegion(),
                    request == null ? null : request.getTravelStyle(),
                    request == null ? null : request.getBudgetLevel(),
                    request == null ? null : request.getCompanionType(),
                    request == null ? null : request.getHeadcount(),
                    request == null ? null : request.getDurationDays(),
                    request == null ? null : request.getActivityTags(),
                    request == null ? null : request.getExcludedActivityTags(),
                    request == null ? null : request.getSoftPenaltyActivityTags(),
                    request == null ? null : request.getPreferredLanguages(),
                    base == null ? 0 : base.getTotalCount(),
                    baseTopScore,
                    finalBase == null ? 0 : finalBase.getTotalCount(),
                    finalTopScore,
                    top1GuideId,
                    top1ReasonCodes,
                    fallback != null && fallback.attemptedRelaxChain(),
                    fallback == null ? null : fallback.winningRelaxStage(),
                    llmParseTrace == null ? null : llmParseTrace.cfgProvider(),
                    llmParseTrace == null ? null : llmParseTrace.extractorBeanClass(),
                    llmParseTrace == null ? null : llmParseTrace.outcome()
            );
        } catch (Exception e) {
            log.debug("[AI_RECOMMEND] logging skipped: {}", e.toString());
        }
    }

    private GuideRecommendResponse applyLlmGuideRankIfEnabled(
            String prompt,
            GuideRecommendRequest scoringRequest,
            int resolvedTopN,
            GuideRecommendResponse finalBase
    ) {
        if (llmGuideRanker == null || !aiProperties.isLlmRankEnabled()) {
            return finalBase;
        }
        if (finalBase == null || finalBase.getRecommendations() == null || finalBase.getRecommendations().isEmpty()) {
            return finalBase;
        }
        if (scoringRequest == null) {
            return finalBase;
        }
        List<GuideRecommendRequest.GuideCandidateDto> pool = scoringRequest.getGuideCandidates();
        if (pool == null || pool.isEmpty()) {
            return finalBase;
        }
        List<GuideRecommendRequest.GuideCandidateDto> filteredPool =
                filterPoolByExcludedSignals(scoringRequest.getExcludedActivityTags(), pool);
        int poolSize = filteredPool.size();
        long t0 = System.nanoTime();
        try {
            GuideRecommendRequest fullReq = scoringRequest.toBuilder()
                    .topN(Math.max(poolSize, resolvedTopN))
                    .build();
            GuideRecommendResponse fullRule = aiRecommendationService.recommend(fullReq);
            if (fullRule.getRecommendations() == null || fullRule.getRecommendations().isEmpty()) {
                metrics.recordLlmGuideRank("empty_rule_full", System.nanoTime() - t0, POLICY_VERSION);
                return finalBase;
            }
            long seed = LlmRankCardComposer.stableSeed(prompt, filteredPool);
            Optional<LlmGuideRankResult> ranked =
                    llmGuideRanker.tryRank(truncateForLlm(prompt), filteredPool, resolvedTopN, seed);
            if (ranked.isEmpty()) {
                metrics.recordLlmGuideRank("empty", System.nanoTime() - t0, POLICY_VERSION);
                log.info("[AI_RANK] llmRank=empty policyVer={} topN={} poolSize={}", POLICY_VERSION, resolvedTopN, poolSize);
                return finalBase;
            }
            GuideRecommendResponse merged = mergeLlmRankIntoResponse(
                    fullRule,
                    ranked.get(),
                    resolvedTopN,
                    scoringRequest.getExcludedActivityTags(),
                    filteredPool
            );
            metrics.recordLlmGuideRank("success", System.nanoTime() - t0, POLICY_VERSION);
            log.info("[AI_RANK] llmRank=success policyVer={} topN={} poolSize={} orderedIds={}",
                    POLICY_VERSION,
                    resolvedTopN,
                    poolSize,
                    ranked.get().orderedGuideIds()
            );
            return merged;
        } catch (Exception e) {
            metrics.recordLlmGuideRank("error", System.nanoTime() - t0, POLICY_VERSION);
            log.warn("[AI_RANK] LLM 순위 적용 실패, 룰 결과 유지: {}", e.toString());
            return finalBase;
        }
    }

    private static GuideRecommendResponse mergeLlmRankIntoResponse(
            GuideRecommendResponse fullRule,
            LlmGuideRankResult rank,
            int topN,
            List<String> excludedActivityTags,
            List<GuideRecommendRequest.GuideCandidateDto> pool
    ) {
        // 2단 필터: LLM이 실수로 제외 후보를 앞에 두더라도, 최종 TopN에서는 제외 위반 후보를 한 번 더 걸러낸다.
        // 단, 너무 과하게 걸러 empty가 되면 원본 머지 결과를 유지한다.
        GuideRecommendResponse baselineMerged = mergeLlmRankIntoResponse(fullRule, rank, topN);
        if (excludedActivityTags == null || excludedActivityTags.isEmpty() || pool == null || pool.isEmpty()) {
            return baselineMerged;
        }

        Map<Long, GuideRecommendRequest.GuideCandidateDto> candidateById = pool.stream()
                .filter(Objects::nonNull)
                .filter(c -> c.getGuideId() != null)
                .collect(Collectors.toMap(GuideRecommendRequest.GuideCandidateDto::getGuideId, c -> c, (a, b) -> a));
        List<String> needles = excludedActivityTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(s -> s.length() >= 2)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (needles.isEmpty()) {
            return baselineMerged;
        }

        List<GuideRecommendItem> src = baselineMerged.getRecommendations();
        if (src == null || src.isEmpty()) {
            return baselineMerged;
        }
        List<GuideRecommendItem> filtered = new ArrayList<>();
        for (GuideRecommendItem it : src) {
            if (filtered.size() >= topN) break;
            if (it == null || it.getGuideId() == null) continue;
            GuideRecommendRequest.GuideCandidateDto c = candidateById.get(it.getGuideId());
            if (c != null && violatesExcludedSignals(needles, c)) {
                continue;
            }
            filtered.add(it);
        }
        if (filtered.isEmpty()) {
            return baselineMerged;
        }
        return GuideRecommendResponse.builder()
                .policyVersion(POLICY_VERSION)
                .totalCount(filtered.size())
                .recommendations(filtered)
                .build();
    }

    private static GuideRecommendResponse mergeLlmRankIntoResponse(
            GuideRecommendResponse fullRule,
            LlmGuideRankResult rank,
            int topN
    ) {
        Map<Long, GuideRecommendItem> byId = fullRule.getRecommendations().stream()
                .filter(it -> it.getGuideId() != null)
                .collect(Collectors.toMap(GuideRecommendItem::getGuideId, it -> it, (a, b) -> a));
        List<GuideRecommendItem> out = new ArrayList<>();
        Set<Long> used = new LinkedHashSet<>();
        Map<Long, String> reasonByGuideId = rank.reasonByGuideId();
        for (Long id : rank.orderedGuideIds()) {
            if (id == null || used.contains(id)) {
                continue;
            }
            GuideRecommendItem it = byId.get(id);
            if (it == null) {
                continue;
            }
            String cleanedReason = sanitizeLlmReason(reasonByGuideId.get(id));
            // LLM reason이 비었거나 너무 약하면 룰 reason을 유지한다.
            GuideRecommendItem built = cleanedReason != null
                    ? it.toBuilder().reason(cleanedReason).comparisonHint(null).build()
                    : it.toBuilder().comparisonHint(null).build();
            out.add(built);
            used.add(id);
            if (out.size() >= topN) {
                break;
            }
        }
        for (GuideRecommendItem x : fullRule.getRecommendations()) {
            if (out.size() >= topN) {
                break;
            }
            if (x.getGuideId() == null || used.contains(x.getGuideId())) {
                continue;
            }
            out.add(x.toBuilder().comparisonHint(null).build());
            used.add(x.getGuideId());
        }
        return GuideRecommendResponse.builder()
                .policyVersion(POLICY_VERSION)
                .totalCount(out.size())
                .recommendations(out)
                .build();
    }

    private static final int LLM_REASON_MAX_CHARS = 160;

    /**
     * LLM이 만든 reason은 사용자 노출 문자열이므로 PII 마스킹/길이 상한을 적용한다.
     * 너무 짧거나 공백뿐이면 null로 간주해 룰 reason을 유지한다.
     */
    static String sanitizeLlmReason(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.strip();
        if (t.isEmpty()) {
            return null;
        }
        // LLM이 이유 끝에 [태그]를 붙여도 UI에서는 제거한다.
        t = t.replaceAll("\\[[^\\]]{1,20}\\]", " ");
        t = t.replaceAll("\\s{2,}", " ").strip();
        t = LlmCopyPiiMasker.mask(t);
        if (t.length() > LLM_REASON_MAX_CHARS) {
            t = t.substring(0, LLM_REASON_MAX_CHARS).strip() + "…";
        }
        // 최소 길이(노이즈 한 단어 방지)
        if (t.length() < 6) {
            return null;
        }
        if (isTooGenericReason(t)) {
            return null;
        }
        if (!isPoliteTone(t)) {
            return null;
        }
        if (containsHedgingOrGuess(t)) {
            return null;
        }
        if (!containsShortQuotation(t)) {
            return null;
        }
        return t;
    }

    /**
     * 인용 근거가 없는 reason은 룰 reason으로 폴백한다.
     * - 따옴표(" 또는 “ ”)로 감싼 3~40자 인용 1개 이상을 요구한다.
     */
    private static boolean containsShortQuotation(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.strip();
        // 우선 큰따옴표 기반으로 검사
        if (hasQuotedSpan(t, '"', '"')) {
            return true;
        }
        // 곡선따옴표(“ ”) 기반으로 검사
        return hasQuotedSpan(t, '“', '”');
    }

    private static boolean hasQuotedSpan(String text, char open, char close) {
        int start = text.indexOf(open);
        while (start >= 0) {
            int end = text.indexOf(close, start + 1);
            if (end < 0) {
                return false;
            }
            int len = end - start - 1;
            if (len >= 3 && len <= 40) {
                return true;
            }
            start = text.indexOf(open, end + 1);
        }
        return false;
    }

    /**
     * UI 톤 통일: 존댓말만 허용. 반말/명령형이면 룰 reason으로 폴백한다.
     */
    private static boolean isPoliteTone(String text) {
        if (text == null) {
            return false;
        }
        String t = text.strip();
        // 존댓말 단서(완벽하진 않지만 실무에서 안정적으로 동작)
        boolean hasPoliteEnding = t.contains("요.") || t.contains("요 ") || t.endsWith("요") || t.contains("습니다") || t.contains("하세요");
        if (!hasPoliteEnding) {
            return false;
        }
        // 흔한 반말/명령형(오탐을 줄이기 위해 보수적으로만)
        String[] banmal = {"해줘", "해라", "하자", "가자", "싶어", "싫어", "야.", "거야", "맞아", "좋아"};
        for (String b : banmal) {
            if (t.contains(b)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 과도한 추측(환각) 문구가 섞이면 사용자 신뢰를 해치므로 폴백한다.
     */
    private static boolean containsHedgingOrGuess(String text) {
        if (text == null) {
            return true;
        }
        String t = text.strip();
        String[] hedges = {"아마", "추정", "추측", "같아요", "같습니다", "일 것", "일것", "일 수도", "일수도", "가능할 것", "가능할것"};
        for (String h : hedges) {
            if (t.contains(h)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 너무 뻔한 문장(근거 없이 '잘 맞아요/추천해요')은 룰 reason으로 폴백한다.
     */
    private static boolean isTooGenericReason(String text) {
        if (text == null) {
            return true;
        }
        String t = text.strip();
        if (t.isEmpty()) {
            return true;
        }
        // 숫자/고유명/구체 근거가 전혀 없고, 일반 칭찬만 있는 경우를 걸러낸다.
        boolean hasDigit = t.matches(".*\\d+.*");
        boolean hasSpecificCue = t.contains("피드") || t.contains("리뷰") || t.contains("평점")
                || t.contains("소개") || t.contains("경력") || t.contains("코스")
                || t.contains("바다") || t.contains("야경") || t.contains("카페") || t.contains("산책");
        if (hasDigit || hasSpecificCue) {
            return false;
        }
        String[] genericPhrases = {
                "잘 맞", "추천", "좋아요", "좋을", "완벽", "최적", "딱이", "적합", "만족", "훌륭", "최고"
        };
        for (String p : genericPhrases) {
            if (t.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private record ParsedPrompt(GuideRecommendRequest request, LlmParseTrace llmTrace) {}

    /**
     * @param cfgProvider      {@code localguest.ai.llm-provider} 정규화 값(로그용)
     * @param extractorBeanClass 주입된 {@link LlmPromptExtractor} 구현 단순 클래스명, 없으면 {@code none}
     * @param outcome          {@code SKIP_DISABLED} | {@code SKIP_NO_BEAN} | {@code SKIP_FASTPATH} | {@code LLM_SUCCESS} | {@code LLM_EMPTY} | {@code LLM_ERROR}
     */
    private record LlmParseTrace(String cfgProvider, String extractorBeanClass, String outcome) {}

    private ParsedPrompt parsePromptToRequest(
            String prompt,
            int resolvedTopN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        String cfgProvider = normalizeCfgLlmProvider(aiProperties.getLlmProvider());
        String extractorBean = llmPromptExtractor == null ? "none" : llmPromptExtractor.getClass().getSimpleName();

        if (!Boolean.TRUE.equals(aiProperties.isLlmPromptExtractionEnabled())) {
            return new ParsedPrompt(
                    promptParser.parse(prompt, resolvedTopN, guideCandidates),
                    new LlmParseTrace(cfgProvider, extractorBean, "SKIP_DISABLED")
            );
        }
        if (llmPromptExtractor == null) {
            return new ParsedPrompt(
                    promptParser.parse(prompt, resolvedTopN, guideCandidates),
                    new LlmParseTrace(cfgProvider, "none", "SKIP_NO_BEAN")
            );
        }

        // 속도 최적화: rank LLM 경로를 쓰는 경우, 룰 파서가 충분히 신뢰할 만하면 LLM 파싱 호출을 생략한다.
        GuideRecommendRequest ruleParsed = promptParser.parse(prompt, resolvedTopN, guideCandidates);
        if (aiProperties.isLlmRankEnabled()
                && notBlank(ruleParsed.getRegion())
                && !"LOW".equals(computePromptParseConfidence(ruleParsed))) {
            metrics.recordLlmPromptExtraction("skip_fastpath", 0L, POLICY_VERSION, cfgProvider);
            return new ParsedPrompt(ruleParsed, new LlmParseTrace(cfgProvider, extractorBean, "SKIP_FASTPATH"));
        }

        String llmPrompt = truncateForLlm(prompt);
        long t0 = System.nanoTime();
        try {
            Optional<GuideRecommendRequest> llm =
                    llmPromptExtractor.tryExtract(llmPrompt, resolvedTopN, guideCandidates);
            long elapsed = System.nanoTime() - t0;
            if (llm.isPresent()) {
                metrics.recordLlmPromptExtraction("success", elapsed, POLICY_VERSION, cfgProvider);
                return new ParsedPrompt(llm.get(), new LlmParseTrace(cfgProvider, extractorBean, "LLM_SUCCESS"));
            }
            metrics.recordLlmPromptExtraction("empty", elapsed, POLICY_VERSION, cfgProvider);
            return new ParsedPrompt(
                    ruleParsed,
                    new LlmParseTrace(cfgProvider, extractorBean, "LLM_EMPTY")
            );
        } catch (Exception e) {
            metrics.recordLlmPromptExtraction("error", System.nanoTime() - t0, POLICY_VERSION, cfgProvider);
            log.warn("[AI_PROMPT] LLM 추출 실패, 룰 파서로 폴백: {}", e.toString());
            return new ParsedPrompt(
                    ruleParsed,
                    new LlmParseTrace(cfgProvider, extractorBean, "LLM_ERROR")
            );
        }
    }

    private static String normalizeCfgLlmProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * LLM 전송 전 원문 길이 제한. 룰 파서 폴백에는 원문 전체를 그대로 쓴다.
     */
    private String truncateForLlm(String prompt) {
        if (prompt == null) {
            return null;
        }
        int max = aiProperties.getLlmPromptMaxChars();
        if (max <= 0 || prompt.length() <= max) {
            return prompt;
        }
        log.warn("[AI_PROMPT] LLM 입력 길이 제한으로 잘림: orig={} → sent={} chars (max={})",
                prompt.length(), max, max);
        return prompt.substring(0, max);
    }

    private static Long topGuideId(GuideRecommendResponse resp) {
        if (resp == null || resp.getRecommendations() == null || resp.getRecommendations().isEmpty()) {
            return null;
        }
        return resp.getRecommendations().get(0).getGuideId();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private enum RelaxStage {
        NONE,
        DROP_ACTIVITY_TAGS_ONLY,
        DROP_TRAVEL_STYLE_ONLY,
        DROP_REGION_ONLY
    }

    private record FallbackOutcome(
            boolean attemptedRelaxChain,
            GuideRecommendResponse responseAfterFallback,
            RelaxStage winningRelaxStage,
            boolean chainExhausted,
            String fallbackNotice,
            List<String> coreNoticeCodes,
            /**
             * {@link #responseAfterFallback()}를 만들 때 사용한 추천 요청(전략 폴백으로 완화된 경우 그 요청).
             * LLM 풀 스코어와 동일 조건을 맞추기 위해 쓴다.
             */
            GuideRecommendRequest requestUsedForRecommend
    ) {
        static FallbackOutcome noRelax(GuideRecommendResponse base, GuideRecommendRequest requestUsedForRecommend) {
            return new FallbackOutcome(false, base, RelaxStage.NONE, false, null, List.of(), requestUsedForRecommend);
        }

        static FallbackOutcome errorNotice(
                GuideRecommendResponse base,
                String notice,
                List<String> codes,
                GuideRecommendRequest requestUsedForRecommend
        ) {
            return new FallbackOutcome(false, base, RelaxStage.NONE, false, notice, codes, requestUsedForRecommend);
        }
    }
}
