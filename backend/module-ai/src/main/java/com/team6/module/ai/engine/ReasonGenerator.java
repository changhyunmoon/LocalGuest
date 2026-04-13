package com.team6.module.ai.engine;

import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.AiRecommendationTuning;
import com.team6.module.ai.support.BudgetTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReasonGenerator {

    private final AdjacentRegionProvider adjacentRegionProvider;

    public static final String CODE_REGION_MATCH = "REGION_MATCH";
    public static final String CODE_REGION_ADJACENT = "REGION_ADJACENT";
    public static final String CODE_STYLE_MATCH = "STYLE_MATCH";
    public static final String CODE_LANGUAGE_MATCH = "LANGUAGE_MATCH";
    public static final String CODE_ACTIVITY_MATCH = "ACTIVITY_MATCH";
    public static final String CODE_SOFT_ACTIVITY_PENALTY = "SOFT_ACTIVITY_PENALTY";
    public static final String CODE_BUDGET_MATCH = "BUDGET_MATCH";
    /** 예산 티어가 한 단계 차이(인접)일 때. {@link #CODE_BUDGET_MATCH}는 완전 일치. */
    public static final String CODE_BUDGET_ADJACENT = "BUDGET_ADJACENT";
    public static final String CODE_GENERAL_FALLBACK = "GENERAL_FALLBACK";
    public static final String CODE_FEEDBACK_REFUND_APPROVED = "FEEDBACK_REFUND_APPROVED";
    public static final String CODE_FEEDBACK_LOW_RATING = "FEEDBACK_LOW_RATING";
    /** 평균 평점이 매우 낮을 때(정책 임계값은 {@link AiRecommendationTuning}). */
    public static final String CODE_FEEDBACK_VERY_LOW_RATING = "FEEDBACK_VERY_LOW_RATING";

    /** 상위 근거 노출 개수(지역·활동·부담·스타일·예산·피드백 등이 겹칠 때 잘리지 않도록 여유). */
    private static final int MAX_DISPLAY_SEGMENTS = 5;

    /**
     * @param score 현재는 reason 문구 생성에 직접 쓰지 않지만, 추후 임계값/요약에 활용 가능하도록 유지
     */
    public ReasonBundle generate(TravelerPreference pref, GuideAiProfile guide, int score) {
        List<Segment> segments = buildSegments(pref, guide);
        if (segments.isEmpty()) {
            segments = List.of(new Segment(
                    CODE_GENERAL_FALLBACK,
                    "선호 조건과 가이드 프로필을 종합해 매칭했습니다",
                    List.of()
            ));
        }

        int limit = Math.min(MAX_DISPLAY_SEGMENTS, segments.size());
        List<Segment> displayed = segments.subList(0, limit);

        String text = displayed.stream()
                .map(Segment::displayText)
                .collect(Collectors.joining(" · "));

        List<String> codes = displayed.stream()
                .map(Segment::code)
                .toList();

        List<ReasonBundle.Fact> facts = displayed.stream()
                .map(s -> ReasonBundle.Fact.builder()
                        .code(s.code)
                        .values(s.factValues)
                        .build())
                .toList();

        return ReasonBundle.builder()
                .text(text)
                .reasonCodes(codes)
                .reasonFacts(facts)
                .build();
    }

    /**
     * 우선순위: 지역 → (선호)활동 → soft 부담 → 스타일 → 언어 → 예산 → 피드백(환불·평점).
     * 상위 {@link #MAX_DISPLAY_SEGMENTS}개만 노출된다.
     */
    private List<Segment> buildSegments(TravelerPreference pref, GuideAiProfile guide) {
        List<Segment> segments = new ArrayList<>();

        if (safeEquals(pref.getRegion(), guide.getRegion())) {
            segments.add(new Segment(
                    CODE_REGION_MATCH,
                    "희망 지역과 가이드 활동 지역이 동일합니다",
                    listNonNull(guide.getRegion())
            ));
        } else if (adjacentRegionProvider.isAdjacentTo(pref.getRegion(), guide.getRegion())) {
            segments.add(new Segment(
                    CODE_REGION_ADJACENT,
                    "희망 지역과 인접한 지역에서 활동합니다",
                    listNonNull(guide.getRegion())
            ));
        }

        if (pref.getActivityTags() != null && guide.getSpecialtyTags() != null) {
            Set<String> guideTags = guide.getSpecialtyTags().stream()
                    .map(KeywordNormalizer::normalizeTag)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toSet());

            List<String> matched = pref.getActivityTags().stream()
                    .map(KeywordNormalizer::normalizeTag)
                    .filter(s -> s != null && !s.isBlank())
                    .filter(guideTags::contains)
                    .distinct()
                    .toList();

            if (!matched.isEmpty()) {
                String head = matched.stream().limit(3).collect(Collectors.joining("/"));
                String suffix = matched.size() > 3 ? " 외" : "";
                String display = "관심 활동 분야가 맞습니다(" + head + suffix + ")";
                segments.add(new Segment(CODE_ACTIVITY_MATCH, display, matched));
            }
        }

        if (pref.getSoftPenaltyActivityTags() != null && guide.getSpecialtyTags() != null) {
            Set<String> guideTagsForPenalty = guide.getSpecialtyTags().stream()
                    .map(KeywordNormalizer::normalizeTag)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toSet());

            List<String> penalized = pref.getSoftPenaltyActivityTags().stream()
                    .map(KeywordNormalizer::normalizeTag)
                    .filter(s -> s != null && !s.isBlank())
                    .filter(guideTagsForPenalty::contains)
                    .distinct()
                    .toList();

            if (!penalized.isEmpty()) {
                String head = penalized.stream().limit(3).collect(Collectors.joining("/"));
                String suffix = penalized.size() > 3 ? " 외" : "";
                String display = "부담되시는 활동이 겹쳐 가중치를 조정했습니다(" + head + suffix + ")";
                segments.add(new Segment(CODE_SOFT_ACTIVITY_PENALTY, display, penalized));
            }
        }

        if (safeEquals(pref.getTravelStyle(), guide.getGuideStyle())) {
            segments.add(new Segment(
                    CODE_STYLE_MATCH,
                    "여행 스타일이 잘 맞습니다",
                    listNonNull(guide.getGuideStyle())
            ));
        }

        if (pref.getPreferredLanguages() != null && guide.getLanguages() != null) {
            Set<String> guideLang = guide.getLanguages().stream()
                    .map(KeywordNormalizer::normalizeLanguage)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toSet());

            LinkedHashSet<String> matchedLanguages = pref.getPreferredLanguages().stream()
                    .map(KeywordNormalizer::normalizeLanguage)
                    .filter(s -> s != null && !s.isBlank())
                    .filter(guideLang::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (!matchedLanguages.isEmpty()) {
                List<String> langValues = new ArrayList<>(matchedLanguages);
                segments.add(new Segment(
                        CODE_LANGUAGE_MATCH,
                        "요청하신 언어 안내가 가능합니다(" + String.join("/", langValues) + ")",
                        langValues
                ));
            }
        }

        if (safeEquals(pref.getBudgetLevel(), guide.getPriceLevel())) {
            segments.add(new Segment(
                    CODE_BUDGET_MATCH,
                    "예산 수준이 동일한 편입니다",
                    listNonNull(guide.getPriceLevel())
            ));
        } else if (BudgetTier.adjacentTiers(pref.getBudgetLevel(), guide.getPriceLevel())) {
            segments.add(new Segment(
                    CODE_BUDGET_ADJACENT,
                    "예산 수준이 한 단계 차이로 가깝습니다",
                    listNonNull(guide.getPriceLevel())
            ));
        }

        if (guide.getApprovedRefundCount() != null && guide.getApprovedRefundCount() > 0) {
            segments.add(new Segment(
                    CODE_FEEDBACK_REFUND_APPROVED,
                    "승인된 환불 이력이 있어 점수에 반영했습니다",
                    List.of(String.valueOf(guide.getApprovedRefundCount()))
            ));
        }

        if (guide.getAverageRating() != null && guide.getReviewCount() != null
                && guide.getReviewCount() >= AiRecommendationTuning.FEEDBACK_LOW_RATING_MIN_REVIEWS) {
            double a = guide.getAverageRating().doubleValue();
            if (a < AiRecommendationTuning.FEEDBACK_VERY_LOW_RATING_THRESHOLD) {
                segments.add(new Segment(
                        CODE_FEEDBACK_VERY_LOW_RATING,
                        "평균 리뷰가 매우 낮아 신중히 반영했습니다",
                        List.of(guide.getAverageRating().toPlainString(), String.valueOf(guide.getReviewCount()))
                ));
            } else if (a < AiRecommendationTuning.FEEDBACK_LOW_RATING_THRESHOLD) {
                segments.add(new Segment(
                        CODE_FEEDBACK_LOW_RATING,
                        "평균 리뷰가 낮은 편이라 보수적으로 반영했습니다",
                        List.of(guide.getAverageRating().toPlainString(), String.valueOf(guide.getReviewCount()))
                ));
            }
        }

        return segments;
    }

    private static List<String> listNonNull(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim());
    }

    private boolean safeEquals(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private record Segment(String code, String displayText, List<String> factValues) {
    }
}
