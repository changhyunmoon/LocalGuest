package com.team6.module.ai.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PromptRecommendApiRequest {
    private String prompt;
    private Integer topN;
    private List<GuideRecommendRequest.GuideCandidateDto> guideCandidates;
}
