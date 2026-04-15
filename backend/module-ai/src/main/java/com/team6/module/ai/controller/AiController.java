package com.team6.module.ai.controller;

import com.team6.module.ai.dto.openapi.OpenApiStandardErrorBody;
import com.team6.module.ai.dto.request.PromptRecommendApiRequest;
import com.team6.module.ai.support.GuideCandidateProvider;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.http.RecommendationHttpHeaders;
import com.team6.module.ai.service.PromptRecommendationService;
import com.team6.module.ai.support.GuideCandidateBundle;
import com.team6.module.ai.parser.PromptParser;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Tag(name = "AI", description = "룰 기반 가이드 추천(프롬프트 파싱 + 스코어링)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private final PromptRecommendationService promptRecommendationService;
    private final GuideCandidateProvider guideCandidateProvider;
    private final PromptParser promptParser;

    @Operation(
            summary = "프롬프트 기반 가이드 추천",
            description = "자연어 프롬프트와 가이드 후보 목록을 받아 상위 N명을 추천합니다. "
                    + "선택 필드 desiredTourDate 또는 desiredTourDateFrom~desiredTourDateTo(yyyy-MM-dd)가 있으면 "
                    + "기간 중 하루라도 결제 완료(BOOKED+isPaid) 스케줄이 있는 가이드는 메인 추천 후보에서 제외합니다. "
                    + "단, 일정 필터만 없었다면 Top1이었을 가이드는 specialSuggestion으로 별도 제시할 수 있습니다. "
                    + "응답의 policyVersion으로 룰 정책 버전을 구분할 수 있습니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "추천 결과",
            headers = @Header(
                    name = RecommendationHttpHeaders.X_RECOMMENDATION_POLICY,
                    description = "룰 정책 버전(응답 body의 policyVersion과 동일). 캐시 키·디버깅에 활용 가능.",
                    schema = @Schema(implementation = String.class, example = "2026.04.15")
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
        LocalDate from = resolveDesiredFromWithPromptFallback(request);
        LocalDate to = resolveDesiredToWithPromptFallback(request, from);

        GuideCandidateBundle bundle =
                guideCandidateProvider.getCandidates(
                        request.getPrompt(),
                        request.getTopN(),
                        request.getGuideCandidates(),
                        from,
                        to
                );

        GuideRecommendResponse main = promptRecommendationService.recommendByPrompt(
                request.getPrompt(),
                request.getTopN(),
                bundle.candidates()
        );

        GuideRecommendResponse.SpecialSuggestion specialSuggestion =
                buildSpecialSuggestionIfNeeded(request, main, bundle);

        GuideRecommendResponse body = (specialSuggestion == null)
                ? main
                : GuideRecommendResponse.builder()
                .conceptSummary(main.getConceptSummary())
                .keywords(main.getKeywords())
                .matchRequestDraft(main.getMatchRequestDraft())
                .notice(main.getNotice())
                .noticeCodes(main.getNoticeCodes())
                .policyVersion(main.getPolicyVersion())
                .promptParseConfidence(main.getPromptParseConfidence())
                .totalCount(main.getTotalCount())
                .recommendations(main.getRecommendations())
                .specialSuggestion(specialSuggestion)
                .build();

        String policy = Objects.requireNonNullElse(body.getPolicyVersion(), "");
        return ResponseEntity.ok()
                .header(RecommendationHttpHeaders.X_RECOMMENDATION_POLICY, policy)
                .body(body);
    }

    private static LocalDate resolveDesiredFrom(PromptRecommendApiRequest request) {
        if (request.getDesiredTourDateFrom() != null) {
            return request.getDesiredTourDateFrom();
        }
        if (request.getDesiredTourDate() != null) {
            return request.getDesiredTourDate();
        }
        return null;
    }

    private static LocalDate resolveDesiredTo(PromptRecommendApiRequest request, LocalDate from) {
        if (request.getDesiredTourDateTo() != null) {
            return request.getDesiredTourDateTo();
        }
        return from;
    }

    private LocalDate resolveDesiredFromWithPromptFallback(PromptRecommendApiRequest request) {
        LocalDate from = resolveDesiredFrom(request);
        if (from != null) {
            return from;
        }
        PromptParser.DesiredDateRange r = promptParser.extractDesiredTourDateRange(request.getPrompt());
        return r == null ? null : r.from();
    }

    private LocalDate resolveDesiredToWithPromptFallback(PromptRecommendApiRequest request, LocalDate from) {
        LocalDate to = resolveDesiredTo(request, from);
        if (to != null) {
            return to;
        }
        PromptParser.DesiredDateRange r = promptParser.extractDesiredTourDateRange(request.getPrompt());
        return r == null ? null : r.to();
    }

    private GuideRecommendResponse.SpecialSuggestion buildSpecialSuggestionIfNeeded(
            PromptRecommendApiRequest request,
            GuideRecommendResponse main,
            GuideCandidateBundle bundle
    ) {
        if (bundle == null || bundle.unfilteredCandidates() == null || bundle.unfilteredCandidates().isEmpty()) {
            return null;
        }
        if (main == null || main.getRecommendations() == null || main.getRecommendations().isEmpty()) {
            // 메인 추천이 비어도, “일정 필터만 없었다면 Top1”이 있으면 특별 제시한다.
            return computeTop1SpecialSuggestion(request, bundle);
        }

        Long mainTop1 = main.getRecommendations().get(0).getGuideId();
        GuideRecommendResponse.SpecialSuggestion special = computeTop1SpecialSuggestion(request, bundle);
        if (special == null || special.getGuide() == null || special.getGuide().getGuideId() == null) {
            return null;
        }
        Long unfilteredTop1 = special.getGuide().getGuideId();
        // 정책: “Top1이 일정 때문에 빠졌을 때만” 특별 제시
        return Objects.equals(mainTop1, unfilteredTop1) ? null : special;
    }

    private GuideRecommendResponse.SpecialSuggestion computeTop1SpecialSuggestion(
            PromptRecommendApiRequest request,
            GuideCandidateBundle bundle
    ) {
        GuideRecommendResponse top1 = promptRecommendationService.recommendByPrompt(
                request.getPrompt(),
                1,
                bundle.unfilteredCandidates()
        );
        if (top1.getRecommendations() == null || top1.getRecommendations().isEmpty()) {
            return null;
        }
        return GuideRecommendResponse.SpecialSuggestion.builder()
                .guide(top1.getRecommendations().get(0))
                .notice("조건에 잘 부합하지만 선택한 날짜에는 예약이 있어요")
                .build();
    }
}