package com.team6.apiserver.dto.request;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PromptRecommendApiRequest {
    private String prompt;
    private List<GuideRecommendRequest.GuideCandidateDto> guideCandidates;
}