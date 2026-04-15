package com.team6.module.ai.engine;

import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.BudgetTier;
import com.team6.module.ai.support.RecommendReasonEvidenceSlots;
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
    private final ScoringPolicySnapshot scoring;

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
    /** 평균 평점이 매우 낮을 때(정책 임계값은 {@link ScoringPolicySnapshot}). */
    public static final String CODE_FEEDBACK_VERY_LOW_RATING = "FEEDBACK_VERY_LOW_RATING";

    private static final List<String> COPY_REGION_EXACT = List.of(
            "희망 지역과 가이드 활동 지역이 동일합니다",
            "요청하신 지역에서 활동 중인 가이드입니다",
            "지역 조건이 그대로 맞아 떨어집니다"
    );

    private static final List<String> COPY_REGION_ADJACENT = List.of(
            "희망 지역과 인접한 지역에서 활동합니다",
            "바로 옆 지역(인접)에서 안내 가능합니다",
            "인접 지역 기준으로 추렸습니다"
    );

    private static final List<String> COPY_ACTIVITY = List.of(
            "관심 활동 분야가 맞습니다({tags})",
            "선호 취미·코스와 전문 분야가 겹칩니다({tags})",
            "가이드 전문과 여행자 관심사가 맞물립니다({tags})"
    );

    private static final List<String> COPY_SOFT_PENALTY = List.of(
            "부담되시는 활동이 겹쳐 가중치를 조정했습니다({tags})",
            "부담 요소가 겹쳐 점수를 보수적으로 반영했습니다({tags})",
            "선호와 상충하는 전문 분야가 있어 가중치를 낮췄습니다({tags})"
    );

    private static final List<String> COPY_STYLE = List.of(
            "여행 스타일이 잘 맞습니다",
            "여행 톤앤매너가 비슷한 편입니다",
            "스타일 취향이 잘 맞물립니다"
    );

    private static final List<String> COPY_LANGUAGE = List.of(
            "요청하신 언어 안내가 가능합니다({langs})",
            "아래 언어로 설명을 도와드릴 수 있어요: {langs}",
            "{langs} 코스 안내가 가능합니다"
    );

    private static final List<String> COPY_BUDGET_EXACT = List.of(
            "예산 수준이 동일한 편입니다",
            "가격대가 잘 맞습니다",
            "예산 톤이 비슷합니다"
    );

    private static final List<String> COPY_BUDGET_ADJ = List.of(
            "예산 수준이 한 단계 차이로 가깝습니다",
            "가격대가 바로 옆 단계입니다",
            "예산이 한 단계만 다른 편입니다"
    );

    private static final List<String> COPY_FEEDBACK_REFUND = List.of(
            "승인된 환불 이력이 있어 점수에 반영했습니다",
            "환불 이력을 보수적으로 반영했습니다",
            "운영 이력(환불)을 근거에 포함했습니다"
    );

    private static final List<String> COPY_FEEDBACK_VERY_LOW = List.of(
            "평균 리뷰가 매우 낮아 신중히 반영했습니다",
            "리뷰 평균이 매우 낮아 가중치를 크게 줄였습니다",
            "평점 신호가 매우 약해 보수적으로 처리했습니다"
    );

    private static final List<String> COPY_FEEDBACK_LOW = List.of(
            "평균 리뷰가 낮은 편이라 보수적으로 반영했습니다",
            "리뷰 평균이 낮아 점수를 조심스럽게 반영했습니다",
            "평점이 낮은 편이라 가중치를 낮췄습니다"
    );

    private static final List<String> COPY_GENERAL_FALLBACK = List.of(
            "선호 조건과 가이드 프로필을 종합해 매칭했습니다",
            "프로필과 선호를 함께 보고 골랐습니다",
            "요청 맥락에 맞춰 후보를 구성했습니다"
    );

    /** 상위 근거 노출 개수(지역·활동·부담·스타일·예산·피드백 등이 겹칠 때 잘리지 않도록 여유). */
    private static final int MAX_DISPLAY_SEGMENTS = 5;

    /**
     * @param score 현재는 reason 문구 생성에 직접 쓰지 않지만, 추후 임계값/요약에 활용 가능하도록 유지
     */
    public ReasonBundle generate(TravelerPreference pref, GuideAiProfile guide, int score) {
        long seed = variantSeed(guide);
        List<Segment> segments = buildSegments(pref, guide, seed);
        if (segments.isEmpty()) {
            segments = List.of(new Segment(
                    CODE_GENERAL_FALLBACK,
                    RecommendReasonEvidenceSlots.GENERAL,
                    pickVariant(seed, CODE_GENERAL_FALLBACK, COPY_GENERAL_FALLBACK),
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
                        .evidenceSlot(s.evidenceSlot)
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
    private List<Segment> buildSegments(TravelerPreference pref, GuideAiProfile guide, long seed) {
        List<Segment> segments = new ArrayList<>();

        if (safeEquals(pref.getRegion(), guide.getRegion())) {
            segments.add(new Segment(
                    CODE_REGION_MATCH,
                    RecommendReasonEvidenceSlots.REGION,
                    pickVariant(seed, CODE_REGION_MATCH, COPY_REGION_EXACT),
                    listNonNull(guide.getRegion())
            ));
        } else if (adjacentRegionProvider.isAdjacentTo(pref.getRegion(), guide.getRegion())) {
            segments.add(new Segment(
                    CODE_REGION_ADJACENT,
                    RecommendReasonEvidenceSlots.REGION_ADJACENT,
                    pickVariant(seed, CODE_REGION_ADJACENT, COPY_REGION_ADJACENT),
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
                String tagToken = head + suffix;
                String shell = pickVariant(seed, CODE_ACTIVITY_MATCH, COPY_ACTIVITY);
                String display = shell.replace("{tags}", tagToken);
                segments.add(new Segment(CODE_ACTIVITY_MATCH, RecommendReasonEvidenceSlots.ACTIVITY_TAGS, display, matched));
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
                String tagToken = head + suffix;
                String shell = pickVariant(seed, CODE_SOFT_ACTIVITY_PENALTY, COPY_SOFT_PENALTY);
                String display = shell.replace("{tags}", tagToken);
                segments.add(new Segment(
                        CODE_SOFT_ACTIVITY_PENALTY,
                        RecommendReasonEvidenceSlots.SOFT_PENALTY_TAGS,
                        display,
                        penalized
                ));
            }
        }

        if (safeEquals(pref.getTravelStyle(), guide.getGuideStyle())) {
            segments.add(new Segment(
                    CODE_STYLE_MATCH,
                    RecommendReasonEvidenceSlots.STYLE,
                    pickVariant(seed, CODE_STYLE_MATCH, COPY_STYLE),
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
                String joined = String.join("/", langValues);
                String shell = pickVariant(seed, CODE_LANGUAGE_MATCH, COPY_LANGUAGE);
                String display = shell.replace("{langs}", joined);
                segments.add(new Segment(
                        CODE_LANGUAGE_MATCH,
                        RecommendReasonEvidenceSlots.LANGUAGE,
                        display,
                        langValues
                ));
            }
        }

        if (safeEquals(pref.getBudgetLevel(), guide.getPriceLevel())) {
            segments.add(new Segment(
                    CODE_BUDGET_MATCH,
                    RecommendReasonEvidenceSlots.BUDGET,
                    pickVariant(seed, CODE_BUDGET_MATCH, COPY_BUDGET_EXACT),
                    listNonNull(guide.getPriceLevel())
            ));
        } else if (BudgetTier.adjacentTiers(pref.getBudgetLevel(), guide.getPriceLevel())) {
            segments.add(new Segment(
                    CODE_BUDGET_ADJACENT,
                    RecommendReasonEvidenceSlots.BUDGET_ADJACENT,
                    pickVariant(seed, CODE_BUDGET_ADJACENT, COPY_BUDGET_ADJ),
                    listNonNull(guide.getPriceLevel())
            ));
        }

        if (guide.getApprovedRefundCount() != null && guide.getApprovedRefundCount() > 0) {
            segments.add(new Segment(
                    CODE_FEEDBACK_REFUND_APPROVED,
                    RecommendReasonEvidenceSlots.FEEDBACK_REFUND,
                    pickVariant(seed, CODE_FEEDBACK_REFUND_APPROVED, COPY_FEEDBACK_REFUND),
                    List.of(String.valueOf(guide.getApprovedRefundCount()))
            ));
        }

        if (guide.getAverageRating() != null && guide.getReviewCount() != null
                && guide.getReviewCount() >= scoring.feedbackLowRatingMinReviews()) {
            double a = guide.getAverageRating().doubleValue();
            if (a < scoring.feedbackVeryLowRatingThreshold()) {
                segments.add(new Segment(
                        CODE_FEEDBACK_VERY_LOW_RATING,
                        RecommendReasonEvidenceSlots.FEEDBACK_RATING,
                        pickVariant(seed, CODE_FEEDBACK_VERY_LOW_RATING, COPY_FEEDBACK_VERY_LOW),
                        List.of(guide.getAverageRating().toPlainString(), String.valueOf(guide.getReviewCount()))
                ));
            } else if (a < scoring.feedbackLowRatingThreshold()) {
                segments.add(new Segment(
                        CODE_FEEDBACK_LOW_RATING,
                        RecommendReasonEvidenceSlots.FEEDBACK_RATING,
                        pickVariant(seed, CODE_FEEDBACK_LOW_RATING, COPY_FEEDBACK_LOW),
                        List.of(guide.getAverageRating().toPlainString(), String.valueOf(guide.getReviewCount()))
                ));
            }
        }

        return segments;
    }

    /**
     * 동일 프롬프트 내에서도 가이드마다 문구가 달라지도록 시드로 쓴다(결정적).
     */
    private static long variantSeed(GuideAiProfile guide) {
        return guide != null && guide.getGuideId() != null ? guide.getGuideId() : 0L;
    }

    private static String pickVariant(long seed, String code, List<String> options) {
        if (options.isEmpty()) {
            return "";
        }
        int idx = Math.floorMod(Long.hashCode(seed) ^ (31 * code.hashCode()), options.size());
        return options.get(idx);
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

    private record Segment(String code, String evidenceSlot, String displayText, List<String> factValues) {
    }
}
