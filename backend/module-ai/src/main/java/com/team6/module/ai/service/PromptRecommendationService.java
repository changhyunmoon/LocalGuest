package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import com.team6.module.ai.support.ConceptSummaryGenerator;
import com.team6.module.ai.support.RecommendationNoticeCodes;
import com.team6.module.ai.support.RegionCandidateExpansion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.team6.module.ai.dto.response.GuideRecommendItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.team6.module.ai.support.AiRecommendationTuning.POLICY_VERSION;

@Service
@Slf4j
public class PromptRecommendationService {

    private final PromptParser promptParser;
    private final AiRecommendationService aiRecommendationService;
    private final AdjacentRegionProvider adjacentRegionProvider;
    private final AiRecommendationMetrics metrics;

    public PromptRecommendationService(
            PromptParser promptParser,
            AiRecommendationService aiRecommendationService,
            AdjacentRegionProvider adjacentRegionProvider,
            AiRecommendationMetrics metrics
    ) {
        this.promptParser = promptParser;
        this.aiRecommendationService = aiRecommendationService;
        this.adjacentRegionProvider = adjacentRegionProvider;
        this.metrics = metrics;
    }

    private static final String NOTICE_REGION_REQUIRED =
            "여행하고 싶은 지역을 알려주시면 더 정확하게 추천할 수 있어요.";
    private static final String NOTICE_ADJACENT_INCLUDED =
            "요청 지역과 가까운 인접 지역 가이드를 포함해 추천했어요.";
    /** API로 넘어온 후보·지역 필터 후 풀에 가이드가 한 명뿐일 때 */
    private static final String NOTICE_SPARSE_GUIDE_POOL =
            "이 조건에 맞는 가이드가 한 분뿐이라 추천 선택 폭이 좁을 수 있어요.";
    private static final String NOTICE_PARSE_LOW =
            "지역 외 조건 신호가 적어 해석 여지가 있어요. 예산·일정·원하는 활동을 조금만 더 적어주시면 정확해져요.";
    private static final String NOTICE_BUDGET_VAGUE =
            "예산과 관련된 표현이 있는데 구간을 확정하지 못했어요. 금액이나 ‘가성비/럭셔리’처럼 알려주시면 좋아요.";
    private static final String NOTICE_DURATION_VAGUE =
            "일정에 대한 말은 있는데 며칠인지 확정하지 못했어요. ‘2박3일’처럼 적어주시면 좋아요.";

    private static final Pattern DURATION_NIGHTS_HINT = Pattern.compile("\\d{1,2}\\s*박");

    public GuideRecommendResponse recommendByPrompt(
            String prompt,
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        long startNs = System.nanoTime();
        metrics.recordPromptRecommendCall();

        try {
            Integer resolvedTopN = (topN == null || topN <= 0)
                    ? AiRecommendationTuning.DEFAULT_TOP_N
                    : topN;
            GuideRecommendRequest parsed = promptParser.parse(prompt, resolvedTopN, guideCandidates);

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
                        0
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

            RegionCandidateExpansion.Result expansion =
                RegionCandidateExpansion.apply(
                        parsed.getGuideCandidates(),
                        parsed.getRegion(),
                        adjacentRegionProvider::neighbors
                );

        GuideRecommendRequest effective = GuideRecommendRequest.builder()
                .region(parsed.getRegion())
                .travelStyle(parsed.getTravelStyle())
                .budgetLevel(parsed.getBudgetLevel())
                .companionType(parsed.getCompanionType())
                .activityTags(parsed.getActivityTags())
                .preferredLanguages(parsed.getPreferredLanguages())
                .headcount(parsed.getHeadcount())
                .durationDays(parsed.getDurationDays())
                .excludedActivityTags(parsed.getExcludedActivityTags())
                .softPenaltyActivityTags(parsed.getSoftPenaltyActivityTags())
                .topN(parsed.getTopN())
                .guideCandidates(expansion.candidates())
                .build();

        GuideRecommendResponse base = aiRecommendationService.recommend(effective);
        FallbackOutcome fallback = resolveFallbackWithStrategicRelaxation(effective, base);
        GuideRecommendResponse finalBase = fallback.responseAfterFallback();

        String parseConfidence = computePromptParseConfidence(parsed);
        List<String> ambiguityCodes = collectAmbiguityNoticeCodes(prompt, parsed);
        List<String> parserHints = parsed.getParserNoticeCodes() == null ? List.of() : parsed.getParserNoticeCodes();

        String notice = fallback.fallbackNotice();
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
        }

            List<String> noticeCodes = buildNoticeCodes(
                    fallback,
                    expansion.expansionUsed(),
                    effectivePoolSizeForNotice,
                    finalBase.getTotalCount(),
                    parserHints,
                    ambiguityCodes,
                    parseConfidence
            );
            metrics.recordOutcome(finalBase.getTotalCount() > 0 ? "success" : "empty");

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
                    expansion.exactCount()
            );

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
            metrics.recordRecommendationLatencyNanos(System.nanoTime() - startNs, POLICY_VERSION);
        }
    }

    private void recordDistributionMetrics(GuideRecommendResponse response, int effectivePoolSize) {
        metrics.recordEffectivePoolSize(effectivePoolSize, POLICY_VERSION);
        if (response.getRecommendations() != null && !response.getRecommendations().isEmpty()) {
            metrics.recordTop1Score(response.getRecommendations().get(0).getScore(), POLICY_VERSION);
        }
    }

    private static GuideRecommendResponse.Keywords keywordsFrom(GuideRecommendRequest request) {
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
        return GuideRecommendResponse.MatchRequestDraft.builder()
                .destination(request.getRegion())
                .concept(ConceptSummaryGenerator.generateMatchRequestConcept(request))
                .conceptSummary(ConceptSummaryGenerator.generate(request))
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
        if (secondary == null || secondary.isBlank()) {
            return primary;
        }
        return primary + " " + secondary;
    }

    private static List<String> buildNoticeCodes(
            FallbackOutcome fallback,
            boolean expansionUsed,
            int effectivePoolSize,
            int resultCount,
            List<String> parserHints,
            List<String> ambiguityCodes,
            String parseConfidence
    ) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (fallback.coreNoticeCodes() != null) {
            codes.addAll(fallback.coreNoticeCodes());
        }
        if (fallback.attemptedRelaxChain()) {
            if (fallback.winningRelaxStage() != RelaxStage.NONE) {
                codes.add(noticeCodeForWinningStage(fallback.winningRelaxStage()));
            } else if (fallback.chainExhausted()) {
                codes.add(RecommendationNoticeCodes.FALLBACK_STRATEGIC_EXHAUSTED);
            }
        }
        if (expansionUsed) {
            codes.add(RecommendationNoticeCodes.ADJACENT_REGION_INCLUDED);
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
        String out = notice;
        if (ambiguityCodes != null && ambiguityCodes.contains(RecommendationNoticeCodes.PROMPT_BUDGET_AMBIGUOUS)) {
            out = mergeNotice(out, NOTICE_BUDGET_VAGUE);
        }
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
        if (!notBlank(parsed.getBudgetLevel()) && budgetMentionedAmbiguous(p)) {
            codes.add(RecommendationNoticeCodes.PROMPT_BUDGET_AMBIGUOUS);
        }
        if (parsed.getDurationDays() == null && durationMentionedAmbiguous(p)) {
            codes.add(RecommendationNoticeCodes.PROMPT_DURATION_AMBIGUOUS);
        }
        return codes;
    }

    private static boolean budgetMentionedAmbiguous(String p) {
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
        int candidateCount = effective.getGuideCandidates() == null ? 0 : effective.getGuideCandidates().size();
        if (candidateCount == 0) {
            return FallbackOutcome.errorNotice(
                    base,
                    "연결 가능한 가이드 후보가 없어 추천을 제공하기 어려워요. 지역을 바꾸거나 나중에 다시 시도해 주세요.",
                    List.of(RecommendationNoticeCodes.NO_GUIDE_CANDIDATES)
            );
        }

        if (!hasAnySignal(effective)) {
            return FallbackOutcome.errorNotice(
                    base,
                    "원하는 조건(지역/스타일/예산/활동/언어/기간/인원)을 조금 더 구체적으로 알려주세요.",
                    List.of(RecommendationNoticeCodes.PROMPT_DETAIL_REQUESTED)
            );
        }

        boolean noResults = base == null || base.getTotalCount() == 0;
        int score = topScore(base);
        boolean lowScore = !noResults && score < AiRecommendationTuning.LOW_SIGNAL_SCORE_THRESHOLD;
        if (!noResults && !lowScore) {
            return FallbackOutcome.noRelax(base);
        }

        List<String> coreCodes = noResults
                ? List.of(RecommendationNoticeCodes.FALLBACK_RELAXED_NO_MATCH)
                : List.of(RecommendationNoticeCodes.FALLBACK_LOW_SCORE_RELAXED);
        String noticeIfExhausted = noResults
                ? "활동·스타일·지역 순으로 조건을 단계적으로 완화해 찾아봤어요."
                : "활동·스타일·지역 순으로 조건을 단계적으로 완화해 추천을 다시 구성해 봤어요.";

        StrategicChainResult chain = runStrategicRelaxationChain(effective, base);
        boolean improved = chain.winningStage() != RelaxStage.NONE;
        GuideRecommendResponse finalResp = improved ? chain.bestResponse() : base;
        boolean exhausted = !improved;
        String notice = improved
                ? "일부 조건을 순서대로 완화해 다시 추천했어요."
                : noticeIfExhausted;

        return new FallbackOutcome(
                true,
                finalResp,
                chain.winningStage(),
                exhausted,
                notice,
                coreCodes
        );
    }

    private StrategicChainResult runStrategicRelaxationChain(
            GuideRecommendRequest languageAnchor,
            GuideRecommendResponse baseline
    ) {
        GuideRecommendRequest current = languageAnchor;
        for (RelaxStage step : STRATEGIC_RELAX_ORDER) {
            if (!isStrategicRelaxApplicable(current, step)) {
                continue;
            }
            current = applyStrategicRelaxStep(languageAnchor, current, step);
            GuideRecommendResponse retried = aiRecommendationService.recommend(current);
            if (acceptsStrategicRetry(retried)) {
                return new StrategicChainResult(retried, step);
            }
        }
        return new StrategicChainResult(baseline, RelaxStage.NONE);
    }

    private static boolean acceptsStrategicRetry(GuideRecommendResponse resp) {
        return resp != null
                && resp.getTotalCount() > 0
                && topScore(resp) >= AiRecommendationTuning.LOW_SIGNAL_SCORE_THRESHOLD;
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

        return GuideRecommendRequest.builder()
                .region(region)
                .travelStyle(style)
                .budgetLevel(current.getBudgetLevel())
                .companionType(current.getCompanionType())
                .activityTags(tags)
                .preferredLanguages(pinnedLangs)
                .headcount(current.getHeadcount())
                .durationDays(current.getDurationDays())
                .excludedActivityTags(current.getExcludedActivityTags())
                .softPenaltyActivityTags(current.getSoftPenaltyActivityTags())
                .topN(current.getTopN())
                .guideCandidates(current.getGuideCandidates())
                .build();
    }

    private record StrategicChainResult(GuideRecommendResponse bestResponse, RelaxStage winningStage) {
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
                || (req.getSoftPenaltyActivityTags() != null && !req.getSoftPenaltyActivityTags().isEmpty());
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
            int expansionExactCount
    ) {
        try {
            int promptHash = Objects.toString(prompt, "").getBytes(StandardCharsets.UTF_8).length == 0
                    ? 0
                    : Objects.toString(prompt, "").hashCode();
            int candidateCount = guideCandidates == null ? 0 : guideCandidates.size();
            int baseTopScore = topScore(base);
            int finalTopScore = topScore(finalBase);
            String top1ReasonCodes = summarizeTopReasonCodes(finalBase);
            Long top1GuideId = topGuideId(finalBase);

            log.info("[AI_RECOMMEND] policyVer={} promptHash={} topN={} candidates={} policy={{noRegionShortCircuit={},regionExpansion={},effectivePool={},expansionExact={}}} keywords={{region={},style={},budget={},companion={},headcount={},durationDays={},tags={},excluded={},softPenalty={},langs={}}} base={{count={},topScore={}}} final={{count={},topScore={},top1GuideId={},top1ReasonCodes={}}} fallback={{used={},stage={}}}",
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
                    fallback == null ? null : fallback.winningRelaxStage()
            );
        } catch (Exception e) {
            log.debug("[AI_RECOMMEND] logging skipped: {}", e.toString());
        }
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
            List<String> coreNoticeCodes
    ) {
        static FallbackOutcome noRelax(GuideRecommendResponse base) {
            return new FallbackOutcome(false, base, RelaxStage.NONE, false, null, List.of());
        }

        static FallbackOutcome errorNotice(GuideRecommendResponse base, String notice, List<String> codes) {
            return new FallbackOutcome(false, base, RelaxStage.NONE, false, notice, codes);
        }
    }
}
