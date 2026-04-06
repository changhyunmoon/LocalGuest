package com.team6.module.ai.controller;

import com.team6.module.ai.dto.request.PromptRecommendApiRequest;
import com.team6.module.ai.support.GuideCandidateProvider;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.service.PromptRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private final PromptRecommendationService promptRecommendationService;
    private final GuideCandidateProvider guideCandidateProvider;

    @PostMapping("/recommend")
    public GuideRecommendResponse recommend(@RequestBody PromptRecommendApiRequest request) {
        List<GuideRecommendRequest.GuideCandidateDto> candidates =
                guideCandidateProvider.getCandidates(request.getGuideCandidates());

        return promptRecommendationService.recommendByPrompt(
                request.getPrompt(),
                candidates
        );
    }
}