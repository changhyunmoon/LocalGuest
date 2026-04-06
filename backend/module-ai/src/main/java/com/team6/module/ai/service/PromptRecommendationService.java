package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.parser.PromptParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptRecommendationService {

    private final PromptParser promptParser;
    private final AiRecommendationService aiRecommendationService;

    public GuideRecommendResponse recommendByPrompt(
            String prompt,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        GuideRecommendRequest request = promptParser.parse(prompt, guideCandidates);
        return aiRecommendationService.recommend(request);
    }
}