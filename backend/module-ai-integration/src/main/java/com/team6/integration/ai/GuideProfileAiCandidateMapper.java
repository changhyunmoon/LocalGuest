package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideProfile;
import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * {@link GuideProfile} → AI 스코어링용 {@link GuideRecommendRequest.GuideCandidateDto} 매핑.
 */
public final class GuideProfileAiCandidateMapper {

    private GuideProfileAiCandidateMapper() {
    }

    public static GuideRecommendRequest.GuideCandidateDto toCandidate(GuideProfile profile) {
        return GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(profile.getId())
                .guideName(profile.getNickname())
                .region(profile.getRegion())
                .guideStyle(null)
                .priceLevel(priceLevelFromHourly(profile.getPricePerHour()))
                .specialtyTags(List.of())
                .languages(splitLanguages(profile.getLanguage()))
                .build();
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
