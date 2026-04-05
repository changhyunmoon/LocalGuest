package com.team6.apiserver.controller;

import com.team6.apiserver.dto.request.PromptRecommendApiRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.service.PromptRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiController {

    private final PromptRecommendationService promptRecommendationService;

    @PostMapping("/recommend")
    public GuideRecommendResponse recommend(@RequestBody PromptRecommendApiRequest request) {
        return promptRecommendationService.recommendByPrompt(
                request.getPrompt(),
                request.getGuideCandidates()
        );
    }
}