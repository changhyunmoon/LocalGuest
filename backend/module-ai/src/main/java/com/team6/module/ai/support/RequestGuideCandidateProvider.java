package com.team6.module.ai.support;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequestGuideCandidateProvider implements com.team6.module.ai.support.GuideCandidateProvider {

    @Override
    public List<GuideRecommendRequest.GuideCandidateDto> getCandidates(
            List<GuideRecommendRequest.GuideCandidateDto> candidates
    ) {
        return candidates == null ? List.of() : candidates;
    }
}