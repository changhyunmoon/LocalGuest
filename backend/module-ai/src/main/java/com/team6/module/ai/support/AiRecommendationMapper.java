package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;

import java.util.List;

public class AiRecommendationMapper {

    private AiRecommendationMapper() {
    }

    public static TravelerPreference toPreference(GuideRecommendRequest request) {
        return TravelerPreference.builder()
                .region(request.getRegion())
                .travelStyle(request.getTravelStyle())
                .budgetLevel(request.getBudgetLevel())
                .companionType(request.getCompanionType())
                .activityTags(request.getActivityTags())
                .preferredLanguages(request.getPreferredLanguages())
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
                        .build())
                .toList();
    }
}