package com.team6.module.ai.engine;

import com.team6.module.ai.config.DiversityRerankSnapshot;
import com.team6.module.ai.dto.response.GuideRecommendItem;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.AiRecommendationMetrics;
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

    // 지역/스타일/예산/활동/언어/피드백 정책을 합산해 "숫자 점수"를 만드는 계산기.
    private final ScoreCalculator scoreCalculator;

    // 점수만 주는 것이 아니라, 왜 추천됐는지 사람이 읽을 수 있는 이유 문구/코드를 만든다.
    private final ReasonGenerator reasonGenerator;

    // 인접 지역 판정은 점수 계산뿐 아니라 다양성 보정, matched evidence 생성에도 쓰인다.
    private final AdjacentRegionProvider adjacentRegionProvider;

    // Top-N이 너무 비슷한 가이드로만 채워지지 않도록 하는 다양성 재정렬 가중치 설정.
    private final DiversityRerankSnapshot diversity;

    // 다양성 패널티 크기 같은 운영 지표를 남긴다.
    private final AiRecommendationMetrics recommendationMetrics;


    public GuideRecommendResponse recommend(
            TravelerPreference preference,
            List<GuideAiProfile> guides,
            int topN
    ) {
        // MatchingEngine은 "후보 가이드 리스트"를 받아 실제 추천 카드 리스트로 바꾸는 마지막 엔진이다.
        // 순서는 대략:
        // 1) 각 가이드 점수 계산
        // 2) 추천 이유 생성
        // 3) 점수순 정렬
        // 4) 다양성 패널티로 Top-N 재선정
        // 5) GuideRecommendItem 응답 형태로 변환
        List<ScoredGuide> scored = guides.stream()
                .map(guide -> {
                    // 점수는 ScoreCalculator가 정책별로 합산한다.
                    int score = scoreCalculator.calculate(preference, guide);
                    // 같은 점수라도 "왜 추천됐는지"가 보여야 프론트/운영/사용자 모두 해석 가능하다.
                    ReasonBundle bundle = reasonGenerator.generate(preference, guide, score);
                    return new ScoredGuide(guide, score, bundle);
                })
                // 1차 정렬은 순수 baseScore 기준이다.
                .sorted(Comparator.comparingInt(ScoredGuide::baseScore).reversed())
                .toList();

        // baseScore가 높더라도 너무 비슷한 가이드만 연속 노출되지 않도록 Top-N을 다시 고른다.
        List<ScoredGuide> picked = pickWithDiversityPenalty(scored, topN, preference);

        // 최종적으로 프론트가 바로 그릴 수 있는 추천 카드 응답으로 변환한다.
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

        // MatchingEngine이 만드는 응답은 "추천 카드 본체"다.
        // conceptSummary / notice / matchRequestDraft 같은 부가 정보는 상위 서비스(PromptRecommendationService)에서 붙인다.
        return GuideRecommendResponse.builder()
                .policyVersion(AiRecommendationTuning.POLICY_VERSION)
                .totalCount(items.size())
                .recommendations(items)
                .build();
    }

    /**
     * 동일 {@code guideId}는 한 번만 선택한다(입력 풀에 중복 ID가 있어도 과다 노출되지 않음).
     */
    private List<ScoredGuide> pickWithDiversityPenalty(
            List<ScoredGuide> candidates,
            int topN,
            TravelerPreference preference
    ) {
        // 이 메서드는 단순히 앞에서 topN개 자르는 것이 아니라,
        // 이미 뽑힌 가이드들과 "얼마나 비슷한지"를 보고 패널티를 준 뒤 다시 고른다.
        if (topN <= 0 || candidates.isEmpty()) {
            return List.of();
        }

        int limit = Math.min(topN, candidates.size());
        List<ScoredGuide> selected = new ArrayList<>(limit);
        Set<Long> used = new HashSet<>();

        while (selected.size() < limit) {
            ScoredGuide best = null;
            double bestFinal = Double.NEGATIVE_INFINITY;
            double bestDiversityPenalty = 0.0;

            for (ScoredGuide c : candidates) {
                // 같은 guideId가 중복 후보로 들어와도 한 번만 노출한다.
                if (c.guide.getGuideId() == null || used.contains(c.guide.getGuideId())) {
                    continue;
                }
                // 이미 뽑힌 가이드와 비슷할수록 패널티가 커진다.
                // 첫 번째 추천은 비교 대상이 없으므로 패널티 없이 그대로 뽑힌다.
                double penalty = selected.isEmpty()
                        ? 0.0
                        : maxSimilarityToSelected(c.guide, selected, preference) * diversity.diversityLambda();
                double finalScore = c.baseScore - penalty;

                if (isBetterCandidate(finalScore, c, bestFinal, best)) {
                    bestFinal = finalScore;
                    best = c;
                    bestDiversityPenalty = penalty;
                }
            }

            if (best == null) {
                break;
            }

            if (!selected.isEmpty()) {
                // 첫 번째 이후 추천에서 실제로 얼마나 패널티가 걸렸는지 운영 지표로 남긴다.
                recommendationMetrics.recordDiversityPenaltyMagnitude(
                        bestDiversityPenalty,
                        AiRecommendationTuning.POLICY_VERSION
                );
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
        // 유사도는 "둘이 얼마나 같은 타입의 가이드인가"를 0~1 사이로 정규화한 값이다.
        // 여기서는 여행자 선호와의 적합도가 아니라, 가이드 프로필끼리의 중복/유사성을 본다.
        double sim = 0.0;

        sim += diversity.regionSimWeight() * regionSimilarityForDiversity(preference, a, b);

        if (safeEquals(a.getGuideStyle(), b.getGuideStyle())) {
            sim += diversity.styleSimWeight();
        }

        double rawTagJaccard = tagOverlapRatio(a.getSpecialtyTags(), b.getSpecialtyTags());
        double effectiveTagJaccard = boostTagJaccardForNearDuplicate(rawTagJaccard);
        sim += diversity.tagSimWeight() * effectiveTagJaccard;

        double langSim = languageJaccard(a.getLanguages(), b.getLanguages());
        sim += diversity.languageSimWeight() * langSim;

        sim += diversity.priceTierSimWeight() * priceTierSimilarity(a.getPriceLevel(), b.getPriceLevel());

        double max = diversity.regionSimWeight()
                + diversity.styleSimWeight()
                + diversity.tagSimWeight()
                + diversity.languageSimWeight()
                + diversity.priceTierSimWeight();
        return max == 0.0 ? 0.0 : (sim / max);
    }

    /**
     * 태그 Jaccard가 높을수록(유사 코스) 유효 유사도를 추가로 올려 Top-N에서 덜 고른다.
     */
    private double boostTagJaccardForNearDuplicate(double jaccard) {
        // 태그가 거의 같은 가이드들은 실제 체감상 "거의 같은 코스"처럼 보일 수 있어서
        // 임계값을 넘으면 유사도를 조금 더 키워 다양성 패널티가 강하게 작동하게 한다.
        if (jaccard < diversity.tagNearDuplicateThreshold()) {
            return jaccard;
        }
        double span = 1.0 - diversity.tagNearDuplicateThreshold();
        if (span <= 0.0) {
            return jaccard;
        }
        double extra = (jaccard - diversity.tagNearDuplicateThreshold()) / span;
        return Math.min(1.0, jaccard + diversity.tagNearDuplicateBoost() * extra);
    }

    /**
     * 0~1. 완전 일치 1, 인접 티어는 {@link DiversityRerankSnapshot#priceAdjacentSimilarityRatio()}, 그 외 0.
     */
    private double priceTierSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        if (a.trim().equalsIgnoreCase(b.trim())) {
            return 1.0;
        }
        if (BudgetTier.adjacentTiers(a, b)) {
            return diversity.priceAdjacentSimilarityRatio();
        }
        return 0.0;
    }

    /**
     * 가이드 둘의 활동 지역이 같으면 1.0. 다르지만 둘 다 여행자 희망 지역 또는 그 인접 지역이면
     * 스냅샷의 regionClusterSimilarityRatio. 그 외 0.
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
            return diversity.regionClusterSimilarityRatio();
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
        // 추천 결과 카드에 보여줄 구조화 근거다.
        // 예: 지역 일치 여부, 예산 인접 여부, 겹친 태그/언어 목록 등
        // 프론트는 이 값을 이용해 배지/툴팁/강조 표시를 할 수 있다.
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
        // 사용자가 "너무 힘든 등산은 부담" 같은 soft 부정을 준 경우,
        // 그 태그를 가이드가 강하게 전문으로 갖고 있으면 어떤 활동이 충돌했는지 노출하기 위한 보조 데이터다.
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
        // ReasonGenerator 내부 근거 팩트를 API 응답용 DTO로 옮긴다.
        // reasonCodes만으로 부족할 때, 어떤 태그/언어/값이 근거였는지 프론트가 상세히 보여줄 수 있다.
        if (bundle == null || bundle.getReasonFacts() == null || bundle.getReasonFacts().isEmpty()) {
            return List.of();
        }
        return bundle.getReasonFacts().stream()
                .map(f -> GuideRecommendItem.ReasonFact.builder()
                        .code(f.getCode())
                        .evidenceSlot(f.getEvidenceSlot())
                        .values(f.getValues())
                        .build())
                .toList();
    }

    // 추천 과정 중 계산 편의를 위한 임시 묶음 객체.
    // 아직 API 응답으로 변환되기 전 단계의 "가이드 + 점수 + 추천 이유" 세트를 보관한다.
    private record ScoredGuide(GuideAiProfile guide, int baseScore, ReasonBundle bundle) {
    }
}
