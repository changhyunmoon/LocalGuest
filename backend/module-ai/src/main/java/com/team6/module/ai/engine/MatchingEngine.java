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

    private static final double DIVERSITY_LAMBDA = 15.0;
    private static final double REGION_SIM_WEIGHT = 1.0;
    private static final double STYLE_SIM_WEIGHT = 0.8;
    private static final double TAG_SIM_WEIGHT = 0.8;

    public GuideRecommendResponse recommend(
            TravelerPreference preference,
            List<GuideAiProfile> guides,
            int topN
    ) {
        List<ScoredGuide> scored = guides.stream()
                .map(guide -> {
                    int score = scoreCalculator.calculate(preference, guide);
                    String reason = reasonGenerator.generate(preference, guide, score);
                    return new ScoredGuide(guide, score, reason);
                })
                .sorted(Comparator.comparingInt(ScoredGuide::baseScore).reversed())
                .toList();

        List<ScoredGuide> picked = pickWithDiversityPenalty(scored, topN);
        List<GuideRecommendItem> items = picked.stream()
                .map(sg -> GuideRecommendItem.builder()
                        .guideId(sg.guide.getGuideId())
                        .guideName(sg.guide.getGuideName())
                        .score(sg.baseScore)
                        .reason(sg.reason)
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
                double penalty = selected.isEmpty() ? 0.0 : maxSimilarityToSelected(c.guide, selected) * DIVERSITY_LAMBDA;
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
            sim += REGION_SIM_WEIGHT;
        }
        if (safeEquals(a.getGuideStyle(), b.getGuideStyle())) {
            sim += STYLE_SIM_WEIGHT;
        }

        double tagSim = tagOverlapRatio(a.getSpecialtyTags(), b.getSpecialtyTags());
        sim += TAG_SIM_WEIGHT * tagSim;

        // normalize to 0..1-ish
        double max = REGION_SIM_WEIGHT + STYLE_SIM_WEIGHT + TAG_SIM_WEIGHT;
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

    private record ScoredGuide(GuideAiProfile guide, int baseScore, String reason) {
    }
}