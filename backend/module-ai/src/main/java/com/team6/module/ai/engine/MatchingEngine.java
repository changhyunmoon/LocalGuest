package com.team6.module.ai.engine;

import com.team6.module.ai.dto.response.GuideRecommendItem;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
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

        List<ScoredGuide> picked = pickWithDiversityPenalty(scored, topN);
        List<GuideRecommendItem> items = picked.stream()
                .map(sg -> GuideRecommendItem.builder()
                        .guideId(sg.guide.getGuideId())
                        .guideName(sg.guide.getGuideName())
                        .score(sg.baseScore)
                        .reason(sg.bundle.getText())
                        .reasonCodes(sg.bundle.getReasonCodes())
                        .reasonFacts(toResponseFacts(sg.bundle))
                        .matched(buildMatchedEvidence(preference, sg.guide))
                        .build())
                .toList();

        return GuideRecommendResponse.builder()
                .totalCount(items.size())
                .recommendations(items)
                .build();
    }

    private List<ScoredGuide> pickWithDiversityPenalty(List<ScoredGuide> candidates, int topN) {
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
                double penalty = selected.isEmpty() ? 0.0 : maxSimilarityToSelected(c.guide, selected) * DiversityRerankConstants.DIVERSITY_LAMBDA;
                double finalScore = c.baseScore - penalty;

                if (finalScore > bestFinal) {
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

    private double maxSimilarityToSelected(GuideAiProfile candidate, List<ScoredGuide> selected) {
        double max = 0.0;
        for (ScoredGuide s : selected) {
            max = Math.max(max, similarity(candidate, s.guide));
        }
        return max;
    }

    private double similarity(GuideAiProfile a, GuideAiProfile b) {
        double sim = 0.0;

        if (safeEquals(a.getRegion(), b.getRegion())) {
            sim += DiversityRerankConstants.REGION_SIM_WEIGHT;
        }
        if (safeEquals(a.getGuideStyle(), b.getGuideStyle())) {
            sim += DiversityRerankConstants.STYLE_SIM_WEIGHT;
        }

        double tagSim = tagOverlapRatio(a.getSpecialtyTags(), b.getSpecialtyTags());
        sim += DiversityRerankConstants.TAG_SIM_WEIGHT * tagSim;

        // normalize to 0..1-ish
        double max = DiversityRerankConstants.REGION_SIM_WEIGHT
                + DiversityRerankConstants.STYLE_SIM_WEIGHT
                + DiversityRerankConstants.TAG_SIM_WEIGHT;
        return max == 0.0 ? 0.0 : (sim / max);
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
        boolean style = safeEquals(pref.getTravelStyle(), guide.getGuideStyle());
        boolean budget = safeEquals(pref.getBudgetLevel(), guide.getPriceLevel());

        List<String> matchedTags = intersectNormalizedTags(pref.getActivityTags(), guide.getSpecialtyTags());
        List<String> matchedLanguages = intersectNormalizedLanguages(pref.getPreferredLanguages(), guide.getLanguages());

        return GuideRecommendItem.MatchedEvidence.builder()
                .region(region)
                .style(style)
                .budget(budget)
                .tags(matchedTags)
                .languages(matchedLanguages)
                .build();
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