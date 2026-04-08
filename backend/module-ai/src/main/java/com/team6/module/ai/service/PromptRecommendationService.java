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
import java.util.Objects;
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
                        .notice(NOTICE_REGION_REQUIRED)
                        .noticeCodes(List.of(RecommendationNoticeCodes.REGION_REQUIRED))
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
        FallbackDecision decision = decideFallback(effective, base);
        GuideRecommendResponse finalBase = base;

        if (decision.shouldFallback) {
            GuideRecommendRequest relaxed = relax(effective, decision.stage);
            GuideRecommendResponse retried = aiRecommendationService.recommend(relaxed);

            if (retried != null && retried.getTotalCount() > 0
                    && topScore(retried) >= AiRecommendationTuning.LOW_SIGNAL_SCORE_THRESHOLD) {
                finalBase = retried;
            }
        }

        String notice = decision.notice;
        if (expansion.expansionUsed()) {
            notice = mergeNotice(NOTICE_ADJACENT_INCLUDED, notice);
            metrics.recordRegionExpansion();
        }
        int effectivePoolSizeForNotice = poolSize(expansion.candidates());
        if (effectivePoolSizeForNotice == 1 && finalBase.getTotalCount() > 0) {
            notice = mergeNotice(NOTICE_SPARSE_GUIDE_POOL, notice);
            metrics.recordSparsePoolNotice();
        }

        if (decision.shouldFallback) {
            metrics.recordFallback(decision.stage.name());
        }

            List<String> noticeCodes = buildNoticeCodes(
                    decision,
                    expansion.expansionUsed(),
                    effectivePoolSizeForNotice,
                    finalBase.getTotalCount()
            );
            metrics.recordOutcome(finalBase.getTotalCount() > 0 ? "success" : "empty");

            logRecommendation(
                    prompt,
                    parsed,
                    guideCandidates,
                    base,
                    finalBase,
                    decision,
                    false,
                    expansion.expansionUsed(),
                    poolSize(expansion.candidates()),
                    expansion.exactCount()
            );

            GuideRecommendResponse out = GuideRecommendResponse.builder()
                    .conceptSummary(ConceptSummaryGenerator.generate(parsed))
                    .keywords(keywordsFrom(parsed))
                    .notice(notice)
                    .noticeCodes(noticeCodes)
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
            FallbackDecision decision,
            boolean expansionUsed,
            int effectivePoolSize,
            int resultCount
    ) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (decision.noticeCodes != null) {
            codes.addAll(decision.noticeCodes);
        }
        if (expansionUsed) {
            codes.add(RecommendationNoticeCodes.ADJACENT_REGION_INCLUDED);
        }
        if (effectivePoolSize == 1 && resultCount > 0) {
            codes.add(RecommendationNoticeCodes.SPARSE_GUIDE_POOL);
        }
        return new ArrayList<>(codes);
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

    private FallbackDecision decideFallback(GuideRecommendRequest request, GuideRecommendResponse base) {
        int candidateCount = request.getGuideCandidates() == null ? 0 : request.getGuideCandidates().size();
        if (candidateCount == 0) {
            return new FallbackDecision(false, RelaxStage.NONE,
                    "연결 가능한 가이드 후보가 없어 추천을 제공하기 어려워요. 지역을 바꾸거나 나중에 다시 시도해 주세요.",
                    List.of(RecommendationNoticeCodes.NO_GUIDE_CANDIDATES));
        }

        int score = topScore(base);
        boolean extractedAnySignal = hasAnySignal(request);
        if (!extractedAnySignal) {
            return new FallbackDecision(false, RelaxStage.NONE,
                    "원하는 조건(지역/스타일/예산/활동/언어/기간/인원)을 조금 더 구체적으로 알려주세요.",
                    List.of(RecommendationNoticeCodes.PROMPT_DETAIL_REQUESTED));
        }

        if (base == null || base.getTotalCount() == 0) {
            return new FallbackDecision(true, RelaxStage.DROP_REGION_STYLE, "조건을 일부 완화해 비슷한 가이드를 다시 찾아봤어요.",
                    List.of(RecommendationNoticeCodes.FALLBACK_RELAXED_NO_MATCH));
        }

        if (score < AiRecommendationTuning.LOW_SIGNAL_SCORE_THRESHOLD) {
            return new FallbackDecision(true, RelaxStage.DROP_REGION, "조건을 일부 완화해 더 많은 후보를 찾아봤어요.",
                    List.of(RecommendationNoticeCodes.FALLBACK_LOW_SCORE_RELAXED));
        }

        return new FallbackDecision(false, RelaxStage.NONE, null, List.of());
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

    /**
     * 조건 완화 단계. {@code excludedActivityTags}·{@code softPenaltyActivityTags}는 사용자 의도로 간주해 항상 원본을 유지한다.
     */
    private GuideRecommendRequest relax(GuideRecommendRequest original, RelaxStage stage) {
        if (original == null) {
            return null;
        }

        String region = original.getRegion();
        String style = original.getTravelStyle();
        List<String> langs = original.getPreferredLanguages();
        List<String> tags = original.getActivityTags();

        if (stage == RelaxStage.DROP_REGION) {
            region = null;
        } else if (stage == RelaxStage.DROP_REGION_STYLE) {
            region = null;
            style = null;
        } else if (stage == RelaxStage.DROP_REGION_STYLE_LANGUAGE) {
            region = null;
            style = null;
            langs = List.of();
        } else if (stage == RelaxStage.DROP_REGION_STYLE_TAGS_LANGUAGE) {
            region = null;
            style = null;
            langs = List.of();
            tags = List.of();
        }

        return GuideRecommendRequest.builder()
                .region(region)
                .travelStyle(style)
                .budgetLevel(original.getBudgetLevel())
                .companionType(original.getCompanionType())
                .activityTags(tags)
                .preferredLanguages(langs)
                .headcount(original.getHeadcount())
                .durationDays(original.getDurationDays())
                .excludedActivityTags(original.getExcludedActivityTags())
                .softPenaltyActivityTags(original.getSoftPenaltyActivityTags())
                .topN(original.getTopN())
                .guideCandidates(original.getGuideCandidates())
                .build();
    }

    private void logRecommendation(
            String prompt,
            GuideRecommendRequest request,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates,
            GuideRecommendResponse base,
            GuideRecommendResponse finalBase,
            FallbackDecision decision,
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
                    decision != null && decision.shouldFallback,
                    decision == null ? null : decision.stage
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
        DROP_REGION,
        DROP_REGION_STYLE,
        DROP_REGION_STYLE_LANGUAGE,
        DROP_REGION_STYLE_TAGS_LANGUAGE
    }

    private static class FallbackDecision {
        private final boolean shouldFallback;
        private final RelaxStage stage;
        private final String notice;
        private final List<String> noticeCodes;

        private FallbackDecision(boolean shouldFallback, RelaxStage stage, String notice, List<String> noticeCodes) {
            this.shouldFallback = shouldFallback;
            this.stage = stage;
            this.notice = notice;
            this.noticeCodes = noticeCodes == null ? List.of() : noticeCodes;
        }
    }
}
