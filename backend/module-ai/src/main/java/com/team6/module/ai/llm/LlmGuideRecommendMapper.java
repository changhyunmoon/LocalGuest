package com.team6.module.ai.llm;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link LlmGuideRecommendJson} → {@link GuideRecommendRequest} (후보·{@code topN}은 호출부에서 고정).
 */
public final class LlmGuideRecommendMapper {

    private LlmGuideRecommendMapper() {
    }

    public static GuideRecommendRequest toRequest(
            LlmGuideRecommendJson j,
            int topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        return GuideRecommendRequest.builder()
                .region(trimToNull(j.getRegion()))
                .travelStyle(trimToNull(j.getTravelStyle()))
                .budgetLevel(trimToNull(j.getBudgetLevel()))
                .budgetMinWon(j.getBudgetMinWon())
                .budgetMaxWon(j.getBudgetMaxWon())
                .budgetScope(trimToNull(j.getBudgetScope()))
                .strictBudget(j.getStrictBudget())
                .companionType(trimToNull(j.getCompanionType()))
                .activityTags(copyList(j.getActivityTags()))
                .requiredActivityTags(copyList(j.getRequiredActivityTags()))
                .niceToHaveActivityTags(copyList(j.getNiceToHaveActivityTags()))
                .preferredLanguages(copyList(j.getPreferredLanguages()))
                .requiredLanguages(copyList(j.getRequiredLanguages()))
                .niceToHaveLanguages(copyList(j.getNiceToHaveLanguages()))
                .allowAdjacentRegion(j.getAllowAdjacentRegion())
                .headcount(j.getHeadcount())
                .durationDays(j.getDurationDays())
                .excludedActivityTags(copyList(j.getExcludedActivityTags()))
                .excludedRegions(copyList(j.getExcludedRegions()))
                .excludedTravelStyles(copyList(j.getExcludedTravelStyles()))
                .excludedLanguages(copyList(j.getExcludedLanguages()))
                .softPenaltyActivityTags(copyList(j.getSoftPenaltyActivityTags()))
                .llmGuideBullets(copyMaskedBulletList(j.getGuideBullets()))
                .llmSpecialRequests(trimToNull(LlmCopyPiiMasker.mask(trimToNull(j.getSpecialRequests()))))
                .topN(topN)
                .guideCandidates(guideCandidates)
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static List<String> copyMaskedBulletList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<String> cleaned = raw.stream()
                .map(s -> s == null ? "" : LlmCopyPiiMasker.mask(s.trim()))
                .filter(StringUtils::hasText)
                .toList();
        return cleaned.isEmpty() ? null : List.copyOf(cleaned);
    }

    private static List<String> copyList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<String> cleaned = raw.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .toList();
        return cleaned.isEmpty() ? null : List.copyOf(cleaned);
    }
}
