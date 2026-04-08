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
            Integer topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        Integer resolvedTopN = (topN == null || topN <= 0) ? 3 : topN;
        GuideRecommendRequest request = promptParser.parse(prompt, resolvedTopN, guideCandidates);
        return aiRecommendationService.recommend(request);
    }
}
