package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.engine.MatchingEngine;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.AiRecommendationMapper;
import com.team6.module.ai.support.AiRecommendationTuning;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiRecommendationServiceImpl implements AiRecommendationService {

    private final MatchingEngine matchingEngine;

    @Override
    public GuideRecommendResponse recommend(GuideRecommendRequest request) {
        if (request == null || request.getGuideCandidates() == null || request.getGuideCandidates().isEmpty()) {
            return GuideRecommendResponse.builder()
                    .policyVersion(AiRecommendationTuning.POLICY_VERSION)
                    .totalCount(0)
                    .recommendations(List.of())
                    .build();
        }

        TravelerPreference preference = AiRecommendationMapper.toPreference(request);
        List<GuideAiProfile> guides = AiRecommendationMapper.toGuideProfiles(request.getGuideCandidates());
        int topN = request.getTopN() == null || request.getTopN() <= 0 ? 3 : request.getTopN();

        Map<Long, Integer> avail = request.getAvailabilityDayCountByGuideId();
        if (avail == null) {
            avail = Map.of();
        }
        return matchingEngine.recommend(preference, guides, topN, avail);
    }
}