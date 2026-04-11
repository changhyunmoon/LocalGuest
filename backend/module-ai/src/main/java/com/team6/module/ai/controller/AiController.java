package com.team6.module.ai.controller;

import com.team6.module.ai.dto.openapi.OpenApiStandardErrorBody;
import com.team6.module.ai.dto.request.PromptRecommendApiRequest;
import com.team6.module.ai.support.GuideCandidateProvider;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.http.RecommendationHttpHeaders;
import com.team6.module.ai.service.PromptRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

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
            headers = @Header(
                    name = RecommendationHttpHeaders.X_RECOMMENDATION_POLICY,
                    description = "룰 정책 버전(응답 body의 policyVersion과 동일). 캐시 키·디버깅에 활용 가능.",
                    schema = @Schema(implementation = String.class, example = "2026.04.6")
            ),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GuideRecommendResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "JSON 형식 오류, 잘못된 Content-Type 등",
            content = @Content(schema = @Schema(implementation = OpenApiStandardErrorBody.class))
    )
    @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = OpenApiStandardErrorBody.class))
    )
    @PostMapping(value = "/recommend", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuideRecommendResponse> recommend(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "프롬프트, topN, 선택적 가이드 후보(비우면 서버가 후보를 채움)",
                    required = true,
                    content = @Content(schema = @Schema(implementation = PromptRecommendApiRequest.class))
            )
            @RequestBody PromptRecommendApiRequest request) {
        List<GuideRecommendRequest.GuideCandidateDto> candidates =
                guideCandidateProvider.getCandidates(
                        request.getPrompt(),
                        request.getTopN(),
                        request.getGuideCandidates()
                );

        GuideRecommendResponse body = promptRecommendationService.recommendByPrompt(
                request.getPrompt(),
                request.getTopN(),
                candidates
        );
        String policy = Objects.requireNonNullElse(body.getPolicyVersion(), "");
        return ResponseEntity.ok()
                .header(RecommendationHttpHeaders.X_RECOMMENDATION_POLICY, policy)
                .body(body);
    }
}