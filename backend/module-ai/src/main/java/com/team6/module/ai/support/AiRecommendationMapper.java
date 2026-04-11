package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.parser.KeywordNormalizer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AiRecommendationMapper {

    private AiRecommendationMapper() {
    }

    public static TravelerPreference toPreference(GuideRecommendRequest request) {
        List<String> excluded = request.getExcludedActivityTags() == null ? List.of() : request.getExcludedActivityTags();
        Set<String> excludedSet = excluded.stream().map(KeywordNormalizer::normalizeTag).collect(Collectors.toSet());
        Set<String> softPenaltySet = request.getSoftPenaltyActivityTags() == null ? Set.of()
                : request.getSoftPenaltyActivityTags().stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        List<String> activityTags = request.getActivityTags() == null ? List.of() : request.getActivityTags();
        List<String> filteredTags = activityTags.stream()
                .map(KeywordNormalizer::normalizeTag)
                .filter(t -> t != null && !t.isBlank())
                .filter(t -> !excludedSet.contains(t))
                .filter(t -> !softPenaltySet.contains(t))
                .distinct()
                .toList();

        return TravelerPreference.builder()
                .region(request.getRegion())
                .travelStyle(request.getTravelStyle())
                .budgetLevel(request.getBudgetLevel())
                .companionType(request.getCompanionType())
                .activityTags(filteredTags)
                .preferredLanguages(request.getPreferredLanguages())
                .headcount(request.getHeadcount())
                .durationDays(request.getDurationDays())
                .excludedActivityTags(excluded)
                .softPenaltyActivityTags(request.getSoftPenaltyActivityTags() == null ? List.of() : request.getSoftPenaltyActivityTags())
                .build();
    }

    public static List<GuideAiProfile> toGuideProfiles(
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        return guideCandidates.stream()
                .map(candidate -> GuideAiProfile.builder()
                        .guideId(candidate.getGuideId())
                        .guideName(candidate.getGuideName())
                        .region(candidate.getRegion())
                        .guideStyle(candidate.getGuideStyle())
                        .priceLevel(candidate.getPriceLevel())
                        .specialtyTags(candidate.getSpecialtyTags())
                        .languages(candidate.getLanguages())
                        .averageRating(candidate.getAverageRating())
                        .reviewCount(candidate.getReviewCount())
                        .approvedRefundCount(candidate.getApprovedRefundCount())
                        .matchRequestCount(candidate.getMatchRequestCount())
                        .progressedMatchCount(candidate.getProgressedMatchCount())
                        .chatStartCount(candidate.getChatStartCount())
                        .build())
                .toList();
    }
}
