package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.engine.MatchingEngine;
import com.team6.module.ai.model.GuideAiProfile;
import com.team6.module.ai.model.TravelerPreference;
import com.team6.module.ai.support.AiRecommendationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiRecommendationServiceImpl implements AiRecommendationService {

    private final MatchingEngine matchingEngine;

    @Override
    public GuideRecommendResponse recommend(GuideRecommendRequest request) {
        if (request == null || request.getGuideCandidates() == null || request.getGuideCandidates().isEmpty()) {
            return GuideRecommendResponse.builder()
                    .totalCount(0)
                    .recommendations(List.of())
                    .build();
        }

        TravelerPreference preference = AiRecommendationMapper.toPreference(request);
        List<GuideAiProfile> guides = AiRecommendationMapper.toGuideProfiles(request.getGuideCandidates());
        int topN = request.getTopN() == null || request.getTopN() <= 0 ? 3 : request.getTopN();

        return matchingEngine.recommend(preference, guides, topN);
    }
}