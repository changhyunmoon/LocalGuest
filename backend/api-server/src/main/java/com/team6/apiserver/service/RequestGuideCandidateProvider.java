package com.team6.apiserver.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequestGuideCandidateProvider implements GuideCandidateProvider {

    @Override
    public List<GuideRecommendRequest.GuideCandidateDto> getCandidates(
            List<GuideRecommendRequest.GuideCandidateDto> candidates
    ) {
        return candidates == null ? List.of() : candidates;
    }
}