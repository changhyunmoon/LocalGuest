package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.support.AiRecommendationTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * {@link GuideProfile} → AI 스코어링용 {@link GuideRecommendRequest.GuideCandidateDto} 매핑.
 */
public final class GuideProfileAiCandidateMapper {

    private GuideProfileAiCandidateMapper() {
    }

    private static final BigDecimal DEFAULT_TOUR_HOURS_PER_DAY = BigDecimal.valueOf(4);
    private static final BigDecimal RANGE_MIN_RATIO = BigDecimal.valueOf(0.8);
    private static final BigDecimal RANGE_MAX_RATIO = BigDecimal.valueOf(1.2);

    public static GuideRecommendRequest.GuideCandidateDto toCandidate(
            GuideProfile profile,
            List<GuideFeed> feeds,
            List<GuideCareer> careers
    ) {
        GuideAiCandidateFeaturesExtractor.Features features =
                GuideAiCandidateFeaturesExtractor.extract(profile, feeds, careers);
        PriceRange priceRange = approximateDailyPriceRangeFromHourly(profile.getPricePerHour());
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
                .build();
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
