package com.team6.module.ai.engine;

import com.team6.module.ai.dto.response.GuideRecommendItem;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.AiRecommendationTuning;
import com.team6.module.ai.support.BudgetTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MatchingEngine {

    private final ScoreCalculator scoreCalculator;
    private final ReasonGenerator reasonGenerator;
    private final AdjacentRegionProvider adjacentRegionProvider;


    public GuideRecommendResponse recommend(
            TravelerPreference preference,
            List<GuideAiProfile> guides,
            int topN
    ) {
        List<ScoredGuide> scored = guides.stream()
                .map(guide -> {
                    int score = scoreCalculator.calculate(preference, guide);
                    ReasonBundle bundle = reasonGenerator.generate(preference, guide, score);
                    return new ScoredGuide(guide, score, bundle);
                })
                .sorted(Comparator.comparingInt(ScoredGuide::baseScore).reversed())
                .toList();

        List<ScoredGuide> picked = pickWithDiversityPenalty(scored, topN, preference);
        List<GuideRecommendItem> items = picked.stream()
                .map(sg -> GuideRecommendItem.builder()
                        .guideId(sg.guide.getGuideId())
                        .guideName(sg.guide.getGuideName())
                        .representativeImageUrl(sg.guide.getRepresentativeImageUrl())
                        .region(sg.guide.getRegion())
                        .priceLevel(sg.guide.getPriceLevel())
                        .averageRating(sg.guide.getAverageRating())
                        .reviewCount(sg.guide.getReviewCount())
                        .publicFeedThumbnailUrls(sg.guide.getPublicFeedThumbnailUrls())
                        .score(sg.baseScore)
                        .reason(sg.bundle.getText())
                        .reasonCodes(sg.bundle.getReasonCodes())
                        .reasonFacts(toResponseFacts(sg.bundle))
                        .matched(buildMatchedEvidence(preference, sg.guide))
                        .build())
                .toList();

        return GuideRecommendResponse.builder()
                .policyVersion(AiRecommendationTuning.POLICY_VERSION)
                .totalCount(items.size())
                .recommendations(items)
                .build();
    }

    private List<ScoredGuide> pickWithDiversityPenalty(
            List<ScoredGuide> candidates,
            int topN,
            TravelerPreference preference
    ) {
        if (topN <= 0 || candidates.isEmpty()) {
            return List.of();
        }

        int limit = Math.min(topN, candidates.size());
        List<ScoredGuide> selected = new ArrayList<>(limit);
        Set<Long> used = new HashSet<>();

        while (selected.size() < limit) {
            ScoredGuide best = null;
            double bestFinal = Double.NEGATIVE_INFINITY;

            for (ScoredGuide c : candidates) {
                if (c.guide.getGuideId() == null || used.contains(c.guide.getGuideId())) {
                    continue;
                }
                double penalty = selected.isEmpty()
                        ? 0.0
                        : maxSimilarityToSelected(c.guide, selected, preference) * DiversityRerankConstants.DIVERSITY_LAMBDA;
                double finalScore = c.baseScore - penalty;

                if (isBetterCandidate(finalScore, c, bestFinal, best)) {
                    bestFinal = finalScore;
                    best = c;
                }
            }

            if (best == null) {
                break;
            }

            selected.add(best);
            used.add(best.guide.getGuideId());
        }

        return selected;
    }

    /**
     * 동일 finalScore일 때 baseScore가 높은 후보, 그다음 guideId 오름차순으로 안정적으로 고른다.
     */
    private static boolean isBetterCandidate(double finalScore, ScoredGuide cand, double bestFinal, ScoredGuide best) {
        int cmp = Double.compare(finalScore, bestFinal);
        if (cmp > 0) {
            return true;
        }
        if (cmp < 0) {
            return false;
        }
        if (best == null) {
            return true;
        }
        if (cand.baseScore != best.baseScore) {
            return cand.baseScore > best.baseScore;
        }
        Long a = cand.guide.getGuideId();
        Long b = best.guide.getGuideId();
        if (a == null) {
            return false;
        }
        if (b == null) {
            return true;
        }
        return a < b;
    }

    private double maxSimilarityToSelected(
            GuideAiProfile candidate,
            List<ScoredGuide> selected,
            TravelerPreference preference
    ) {
        double max = 0.0;
        for (ScoredGuide s : selected) {
            max = Math.max(max, similarity(preference, candidate, s.guide));
        }
        return max;
    }

    /**
     * 0~1에 가깝게 정규화한 프로필 유사도. 여행자 희망 지역이 있으면 인접권 안의 서로 다른 지역도 부분 유사로 본다.
     */
    private double similarity(TravelerPreference preference, GuideAiProfile a, GuideAiProfile b) {
        double sim = 0.0;

        sim += DiversityRerankConstants.REGION_SIM_WEIGHT * regionSimilarityForDiversity(preference, a, b);

        if (safeEquals(a.getGuideStyle(), b.getGuideStyle())) {
            sim += DiversityRerankConstants.STYLE_SIM_WEIGHT;
        }

        double tagSim = tagOverlapRatio(a.getSpecialtyTags(), b.getSpecialtyTags());
        sim += DiversityRerankConstants.TAG_SIM_WEIGHT * tagSim;

        double langSim = languageJaccard(a.getLanguages(), b.getLanguages());
        sim += DiversityRerankConstants.LANGUAGE_SIM_WEIGHT * langSim;

        if (safeEquals(a.getPriceLevel(), b.getPriceLevel())) {
            sim += DiversityRerankConstants.PRICE_TIER_SIM_WEIGHT;
        }

        double max = DiversityRerankConstants.REGION_SIM_WEIGHT
                + DiversityRerankConstants.STYLE_SIM_WEIGHT
                + DiversityRerankConstants.TAG_SIM_WEIGHT
                + DiversityRerankConstants.LANGUAGE_SIM_WEIGHT
                + DiversityRerankConstants.PRICE_TIER_SIM_WEIGHT;
        return max == 0.0 ? 0.0 : (sim / max);
    }

    /**
     * 가이드 둘의 활동 지역이 같으면 1.0. 다르지만 둘 다 여행자 희망 지역 또는 그 인접 지역이면
     * {@link DiversityRerankConstants#REGION_CLUSTER_SIMILARITY_RATIO}. 그 외 0.
     */
    private double regionSimilarityForDiversity(TravelerPreference preference, GuideAiProfile a, GuideAiProfile b) {
        String ra = a.getRegion();
        String rb = b.getRegion();
        if (ra == null || rb == null) {
            return 0.0;
        }
        if (ra.equalsIgnoreCase(rb)) {
            return 1.0;
        }
        if (preference == null || preference.getRegion() == null || preference.getRegion().isBlank()) {
            return 0.0;
        }
        String tr = preference.getRegion().trim();
        boolean aInTravelZone = tr.equalsIgnoreCase(ra.trim()) || adjacentRegionProvider.isAdjacentTo(tr, ra);
        boolean bInTravelZone = tr.equalsIgnoreCase(rb.trim()) || adjacentRegionProvider.isAdjacentTo(tr, rb);
        if (aInTravelZone && bInTravelZone) {
            return DiversityRerankConstants.REGION_CLUSTER_SIMILARITY_RATIO;
        }
        return 0.0;
    }

    private double languageJaccard(List<String> la, List<String> lb) {
        if (la == null || lb == null || la.isEmpty() || lb.isEmpty()) {
            return 0.0;
        }
        Set<String> a = la.stream()
                .map(KeywordNormalizer::normalizeLanguage)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> b = lb.stream()
                .map(KeywordNormalizer::normalizeLanguage)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new LinkedHashSet<>(a);
        inter.retainAll(b);
        Set<String> uni = new LinkedHashSet<>(a);
        uni.addAll(b);
        return uni.isEmpty() ? 0.0 : ((double) inter.size() / (double) uni.size());
    }

    private double tagOverlapRatio(List<String> tagsA, List<String> tagsB) {
        if (tagsA == null || tagsB == null || tagsA.isEmpty() || tagsB.isEmpty()) {
            return 0.0;
        }
        Set<String> a = tagsA.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> b = tagsB.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        Set<String> inter = new LinkedHashSet<>(a);
        inter.retainAll(b);
        Set<String> uni = new LinkedHashSet<>(a);
        uni.addAll(b);

        return uni.isEmpty() ? 0.0 : ((double) inter.size() / (double) uni.size());
    }

    private boolean safeEquals(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private GuideRecommendItem.MatchedEvidence buildMatchedEvidence(TravelerPreference pref, GuideAiProfile guide) {
        boolean region = safeEquals(pref.getRegion(), guide.getRegion());
        boolean regionAdjacent = !region && adjacentRegionProvider.isAdjacentTo(pref.getRegion(), guide.getRegion());
        boolean style = safeEquals(pref.getTravelStyle(), guide.getGuideStyle());
        boolean budgetExact = safeEquals(pref.getBudgetLevel(), guide.getPriceLevel());
        boolean budgetAdjacent = !budgetExact
                && BudgetTier.adjacentTiers(pref.getBudgetLevel(), guide.getPriceLevel());

        List<String> matchedTags = intersectNormalizedTags(pref.getActivityTags(), guide.getSpecialtyTags());
        List<String> matchedLanguages = intersectNormalizedLanguages(pref.getPreferredLanguages(), guide.getLanguages());
        List<String> softPenaltyOverlap = softPenaltyOverlapTags(pref.getSoftPenaltyActivityTags(), guide.getSpecialtyTags());

        return GuideRecommendItem.MatchedEvidence.builder()
                .region(region)
                .regionAdjacent(regionAdjacent)
                .style(style)
                .budget(budgetExact)
                .budgetAdjacent(budgetAdjacent)
                .tags(matchedTags)
                .languages(matchedLanguages)
                .softPenaltyOverlapTags(softPenaltyOverlap)
                .build();
    }

    private List<String> softPenaltyOverlapTags(List<String> softPenaltyTags, List<String> guideSpecialtyTags) {
        if (softPenaltyTags == null || guideSpecialtyTags == null || softPenaltyTags.isEmpty() || guideSpecialtyTags.isEmpty()) {
            return List.of();
        }
        Set<String> guideNorm = guideSpecialtyTags.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        return softPenaltyTags.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .filter(guideNorm::contains)
                .distinct()
                .toList();
    }

    private List<String> intersectNormalizedTags(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return List.of();
        }
        Set<String> right = b.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        return a.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .filter(right::contains)
                .toList();
    }

    private List<String> intersectNormalizedLanguages(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return List.of();
        }
        Set<String> right = b.stream()
                .map(KeywordNormalizer::normalizeLanguage)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        return a.stream()
                .map(KeywordNormalizer::normalizeLanguage)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .filter(right::contains)
                .toList();
    }

    private List<GuideRecommendItem.ReasonFact> toResponseFacts(ReasonBundle bundle) {
        if (bundle == null || bundle.getReasonFacts() == null || bundle.getReasonFacts().isEmpty()) {
            return List.of();
        }
        return bundle.getReasonFacts().stream()
                .map(f -> GuideRecommendItem.ReasonFact.builder()
                        .code(f.getCode())
                        .values(f.getValues())
                        .build())
                .toList();
    }

    private record ScoredGuide(GuideAiProfile guide, int baseScore, ReasonBundle bundle) {
    }
}