package com.team6.apiserver.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;

import java.util.List;

public interface GuideCandidateProvider {
    List<GuideRecommendRequest.GuideCandidateDto> getCandidates(
            List<GuideRecommendRequest.GuideCandidateDto> candidates
    );
}