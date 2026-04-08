package com.team6.module.ai.controller;

import com.team6.module.ai.dto.request.PromptRecommendApiRequest;
import com.team6.module.ai.support.GuideCandidateProvider;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.service.PromptRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI", description = "룰 기반 가이드 추천(프롬프트 파싱 + 스코어링)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private final PromptRecommendationService promptRecommendationService;
    private final GuideCandidateProvider guideCandidateProvider;

    @Operation(
            summary = "프롬프트 기반 가이드 추천",
            description = "자연어 프롬프트와 가이드 후보 목록을 받아 상위 N명을 추천합니다. "
                    + "응답의 policyVersion으로 룰 정책 버전을 구분할 수 있습니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "추천 결과",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GuideRecommendResponse.class))
    )
    @PostMapping(value = "/recommend", produces = MediaType.APPLICATION_JSON_VALUE)
    public GuideRecommendResponse recommend(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "프롬프트, topN, 선택적 가이드 후보(비우면 서버가 후보를 채움)",
                    required = true,
                    content = @Content(schema = @Schema(implementation = PromptRecommendApiRequest.class))
            )
            @RequestBody PromptRecommendApiRequest request) {
        List<GuideRecommendRequest.GuideCandidateDto> candidates =
                guideCandidateProvider.getCandidates(request.getGuideCandidates());

        return promptRecommendationService.recommendByPrompt(
                request.getPrompt(),
                request.getTopN(),
                candidates
        );
    }
}