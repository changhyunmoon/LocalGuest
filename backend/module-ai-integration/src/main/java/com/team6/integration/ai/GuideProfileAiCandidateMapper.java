package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.support.AiRecommendationTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link GuideProfile} → AI 스코어링용 {@link GuideRecommendRequest.GuideCandidateDto} 매핑.
 */
public final class GuideProfileAiCandidateMapper {

    private GuideProfileAiCandidateMapper() {
    }

    private static final BigDecimal DEFAULT_TOUR_HOURS_PER_DAY = BigDecimal.valueOf(4);
    private static final BigDecimal RANGE_MIN_RATIO = BigDecimal.valueOf(0.8);
    private static final BigDecimal RANGE_MAX_RATIO = BigDecimal.valueOf(1.2);

    private static final int LLM_INTRO_MAX_CHARS = 900;
    private static final int LLM_FEED_BODY_EACH_MAX = 450;
    private static final int LLM_FEED_MAX_ITEMS = 12;
    private static final int LLM_CAREER_SNIPPET_MAX = 650;
    private static final int LLM_DEFAULT_COURSE_MAX = 400;

    public static GuideRecommendRequest.GuideCandidateDto toCandidate(
            GuideProfile profile,
            List<GuideFeed> feeds,
            List<GuideCareer> careers
    ) {
        GuideAiCandidateFeaturesExtractor.Features features =
                GuideAiCandidateFeaturesExtractor.extract(profile, feeds, careers);
        PriceRange priceRange = approximateDailyPriceRangeFromHourly(profile.getPricePerHour());
        List<GuideFeed> visibleFeeds = visibleFeedsSorted(feeds);
        return GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(profile.getId())
                .guideName(profile.getNickname())
                .region(profile.getRegion())
                .guideStyle(features.guideStyle())
                .priceLevel(priceLevelFromHourly(profile.getPricePerHour()))
                .priceMinWon(priceRange == null ? null : priceRange.minWon())
                .priceMaxWon(priceRange == null ? null : priceRange.maxWon())
                .priceScope(priceRange == null ? null : priceRange.scope())
                .specialtyTags(features.specialtyTags())
                .languages(splitLanguages(profile.getLanguage()))
                .averageRating(profile.getAverageRating())
                .reviewCount(profile.getReviewCount())
                .approvedRefundCount(null)
                .representativeImageUrl(profile.getProfileImage())
                .publicFeedThumbnailUrls(publicFeedThumbnailUrls(feeds, AiRecommendationTuning.PUBLIC_FEED_THUMBNAIL_MAX))
                .llmIntroSnippet(buildLlmIntroSnippet(profile))
                .llmFeedBodiesNewestFirst(buildLlmFeedBodies(visibleFeeds))
                .llmCareerSnippet(buildLlmCareerSnippet(careers))
                .llmDefaultCourseSnippet(truncate(profile == null ? null : profile.getDefaultCourse(), LLM_DEFAULT_COURSE_MAX))
                .publicFeedCount(visibleFeeds.size())
                .build();
    }

    private static List<GuideFeed> visibleFeedsSorted(List<GuideFeed> feeds) {
        if (feeds == null || feeds.isEmpty()) {
            return List.of();
        }
        return feeds.stream()
                .filter(f -> f != null && !Boolean.TRUE.equals(f.getIsDeleted()))
                .sorted(Comparator.comparing(GuideFeed::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    private static String buildLlmIntroSnippet(GuideProfile profile) {
        if (profile == null) {
            return "";
        }
        String merged = TextJoin.twoBlocks(profile.getBio(), profile.getLocalStory());
        return truncate(merged, LLM_INTRO_MAX_CHARS);
    }

    private static List<String> buildLlmFeedBodies(List<GuideFeed> visibleFeeds) {
        if (visibleFeeds.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(Math.min(visibleFeeds.size(), LLM_FEED_MAX_ITEMS));
        for (GuideFeed f : visibleFeeds) {
            if (out.size() >= LLM_FEED_MAX_ITEMS) {
                break;
            }
            String c = f.getContent();
            if (c == null || c.isBlank()) {
                continue;
            }
            out.add(truncate(c.strip(), LLM_FEED_BODY_EACH_MAX));
        }
        return List.copyOf(out);
    }

    private static String buildLlmCareerSnippet(List<GuideCareer> careers) {
        if (careers == null || careers.isEmpty()) {
            return "";
        }
        String joined = careers.stream()
                .filter(c -> c != null && c.getTitle() != null && !c.getTitle().isBlank())
                .map(c -> {
                    String t = c.getTitle().strip();
                    String d = c.getDescription() == null ? "" : truncate(c.getDescription().strip(), 180);
                    return d.isEmpty() ? t : t + ": " + d;
                })
                .collect(Collectors.joining(" | "));
        return truncate(joined, LLM_CAREER_SNIPPET_MAX);
    }

    private static String truncate(String s, int maxChars) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (maxChars <= 0 || s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "…";
    }

    private static final class TextJoin {
        private TextJoin() {
        }

        static String twoBlocks(String a, String b) {
            String x = a == null ? "" : a.strip();
            String y = b == null ? "" : b.strip();
            if (x.isEmpty()) {
                return y;
            }
            if (y.isEmpty()) {
                return x;
            }
            return x + "\n\n" + y;
        }
    }

    private record PriceRange(Integer minWon, Integer maxWon, String scope) {
    }

    /**
     * 현재 가이드 가격 계약은 {@code pricePerHour}만 존재하므로, 범위 기반 예산 매칭이 동작할 수 있게
     * "1일 투어(대략 4시간)"를 기준으로 완충 범위를 만든다.
     * <p>
     * - scope: {@code per_day}
     * - range: hourly * 4h * (0.8~1.2)
     */
    private static PriceRange approximateDailyPriceRangeFromHourly(BigDecimal pricePerHour) {
        if (pricePerHour == null) {
            return null;
        }
        BigDecimal daily = pricePerHour.multiply(DEFAULT_TOUR_HOURS_PER_DAY);
        if (daily.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        int min = daily.multiply(RANGE_MIN_RATIO).setScale(0, RoundingMode.HALF_UP).intValue();
        int max = daily.multiply(RANGE_MAX_RATIO).setScale(0, RoundingMode.HALF_UP).intValue();
        if (min <= 0 || max <= 0) {
            return null;
        }
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        return new PriceRange(min, max, "per_day");
    }

    private static List<String> publicFeedThumbnailUrls(List<GuideFeed> feeds, int max) {
        if (feeds == null || feeds.isEmpty() || max <= 0) {
            return List.of();
        }
        return feeds.stream()
                .filter(f -> f.getImageUrl() != null && !f.getImageUrl().isBlank())
                .sorted(Comparator.comparing(GuideFeed::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(GuideFeed::getImageUrl)
                .limit(max)
                .toList();
    }

    private static String priceLevelFromHourly(BigDecimal pricePerHour) {
        if (pricePerHour == null) {
            return null;
        }
        if (pricePerHour.compareTo(BigDecimal.valueOf(50_000)) < 0) {
            return "낮음";
        }
        if (pricePerHour.compareTo(BigDecimal.valueOf(100_000)) < 0) {
            return "중간";
        }
        return "높음";
    }

    private static List<String> splitLanguages(String language) {
        if (language == null || language.isBlank()) {
            return List.of();
        }
        return Arrays.stream(language.split("[,，/|]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
