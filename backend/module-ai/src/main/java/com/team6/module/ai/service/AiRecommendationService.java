package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;

public interface AiRecommendationService {
    GuideRecommendResponse recommend(GuideRecommendRequest request);
}