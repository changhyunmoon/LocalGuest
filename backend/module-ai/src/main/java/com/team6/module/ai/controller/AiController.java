package com.team6.module.ai.controller;

import com.team6.module.ai.dto.openapi.OpenApiStandardErrorBody;
import com.team6.module.ai.dto.request.AiRecommendClickRequest;
import com.team6.module.ai.dto.request.PromptRecommendApiRequest;
import com.team6.module.ai.support.GuideCandidateProvider;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendItem;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.http.RecommendationHttpHeaders;
import com.team6.module.ai.service.PromptRecommendationService;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import com.team6.module.ai.support.AiRecommendClickStore;
import com.team6.module.ai.support.GuideCandidateBundle;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.support.GuideAvailabilityProvider;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

@Tag(name = "AI", description = "룰 기반 가이드 추천(프롬프트 파싱 + 스코어링)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
@Slf4j
public class AiController {

    // F03 추천 파이프라인의 메인 조립자.
    // 실제로는 PromptRecommendationService -> AiRecommendationServiceImpl -> MatchingEngine 순으로 이어지며,
    // 프롬프트 파싱, 후보 보정, fallback 재시도, conceptSummary/matchRequestDraft 생성까지 담당한다.
    private final PromptRecommendationService promptRecommendationService;

    // 추천 비교 대상이 될 가이드 후보군을 준비한다.
    // 현재 기본 구현은 @Primary가 붙은 DbBackedGuideCandidateProvider로 연결되며,
    // 프론트가 후보를 직접 보내지 않으면 DB에서 승인/활성 가이드, 공개 피드, 경력,
    // 일부 운영 신호(환불/행동 데이터 등)를 합쳐 AI 후보 DTO로 채워 넣는다.
    private final GuideCandidateProvider guideCandidateProvider;

    // 사용자가 날짜를 별도 필드로 주지 않은 경우, 프롬프트 문장에서 희망 투어 날짜 범위를 추출하는 fallback 용도다.
    private final PromptParser promptParser;

    // 일정 필터에 걸려 메인 추천에서 빠진 가이드에 대해, 가능한 날짜를 안내하기 위한 조회용 provider다.
    private final GuideAvailabilityProvider guideAvailabilityProvider;

    private final AiRecommendationMetrics recommendationMetrics;
    private final AiRecommendClickStore clickStore;

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
                    schema = @Schema(implementation = String.class, example = "2026.04.26")
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
        // 1) 투어 희망 날짜를 확정한다.
        // 우선순위는 요청 필드 > 프롬프트 문장 파싱 결과이며, 이후 후보 필터링/특별 제시에 함께 사용된다.
        // 즉 프론트가 날짜를 명시하지 않아도 "4/20~4/22", "다음 주말" 같은 문장에서
        // 일정 범위를 뽑아 추천 후보 필터에 활용하려는 목적이다.
        LocalDate from = resolveDesiredFromWithPromptFallback(request);
        LocalDate to = resolveDesiredToWithPromptFallback(request, from);

        // 2) 추천 대상 가이드 후보를 모은다.
        // 실제로는 GuideCandidateProvider 구현체인 DbBackedGuideCandidateProvider#getCandidates(...)가 주로 실행된다.
        // 이 단계에서 일정 충돌이 있는 가이드는 메인 후보에서 빠질 수 있고,
        // 동시에 "일정만 아니면 추천됐을 후보"를 위해 unfilteredCandidates도 함께 보관한다.
        // 그래서 반환 타입도 단순 List가 아니라 GuideCandidateBundle이다.
        GuideCandidateBundle rawBundle =
                guideCandidateProvider.getCandidates(
                        request.getPrompt(),
                        request.getTopN(),
                        request.getGuideCandidates(),
                        from,
                        to
                );
        GuideCandidateBundle bundle = enrichCandidatesWithClickCounts(rawBundle);

        // 3) 실제 메인 추천을 수행한다.
        // 내부에서는 프롬프트 파싱 -> 후보 보정 -> 점수 계산 -> 이유 생성 -> 응답 생성 흐름으로 이어진다.
        // 여기서 만들어지는 main은 "일정 필터를 통과한 후보들만" 대상으로 계산된 기본 추천 결과다.
        Map<Long, Integer> availabilityDays = buildAvailabilityDayCounts(bundle, from, to);

        GuideRecommendResponse main = promptRecommendationService.recommendByPrompt(
                request.getPrompt(),
                request.getTopN(),
                bundle.candidates(),
                availabilityDays.isEmpty() ? null : availabilityDays
        );

        // 4) 일정 때문에 메인 추천에서 빠졌지만, 원래는 Top1이었을 가이드를 특별 제안(specialSuggestion)으로 만들지 판단한다.
        // 사용자가 원하는 조건에는 잘 맞지만 선택 날짜에 예약이 있어 빠진 경우를
        // "아예 숨기지 말고 별도 카드로 보여주자"는 UX 목적의 보조 흐름이다.
        GuideRecommendResponse.SpecialSuggestion specialSuggestion =
                buildSpecialSuggestionIfNeeded(request, main, bundle, from, to, availabilityDays);

        // 5) 최종 응답을 구성한다.
        // specialSuggestion이 없으면 메인 추천 응답을 그대로 쓰고,
        // 있으면 메인 추천 카드 + 특별 제안 카드를 함께 내려 프론트가 별도 섹션으로 보여줄 수 있게 한다.
        // 즉 recommendations는 "지금 바로 추천 가능한 가이드", specialSuggestion은
        // "조건은 맞지만 일정 때문에 메인 추천에서 빠진 가이드"라고 이해하면 된다.
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

        // 날짜 필터로 후보가 모두 빠진 경우(원본 후보는 있었음)를 notice로 더 분명히 안내한다.
        body = enrichNoticeForDateFilteredEmpty(request, bundle, from, to, body);

        // 6) 정책 버전을 헤더와 body에 함께 실어 응답한다.
        // 프론트는 recommendations로 추천 카드 UI를 그리고,
        // matchRequestDraft는 이후 매칭 요청 생성 단계의 기본값으로 재사용할 수 있다.
        // policyVersion은 추천 규칙이 바뀌었는지 확인하는 디버깅/캐시 키 용도로 같이 내려간다.
        String policy = Objects.requireNonNullElse(body.getPolicyVersion(), "");
        return ResponseEntity.ok()
                .header(RecommendationHttpHeaders.X_RECOMMENDATION_POLICY, policy)
                .body(body);
    }

    @Operation(
            summary = "추천 카드 클릭 이벤트 수집",
            description = "추천 결과 카드 클릭 로그(관심/탐색 신호). 2단계에서는 in-memory 집계로 추천 보정에 활용합니다."
    )
    @ApiResponse(responseCode = "204", description = "수집 성공(응답 바디 없음)")
    @PostMapping(value = "/recommend/click", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordRecommendClick(@RequestBody AiRecommendClickRequest request) {
        String pv = (request == null || request.getPolicyVersion() == null || request.getPolicyVersion().isBlank())
                ? AiRecommendationTuning.POLICY_VERSION
                : request.getPolicyVersion();
        Integer rank = request == null ? null : request.getRank();
        Long guideId = request == null ? null : request.getGuideId();
        String clientReqId = request == null ? null : request.getClientRequestId();

        recommendationMetrics.recordRecommendationClick(rank, pv);
        clickStore.recordClick(guideId);

        String prompt = request == null ? null : request.getPrompt();
        Integer promptHash = (prompt == null || prompt.isBlank()) ? null : promptHash(prompt);
        log.info("[AI_RECOMMEND_CLICK] policyVer={} guideId={} rank={} promptHash={} clientReqId={}",
                pv, guideId, rank, promptHash, clientReqId);
    }

    private GuideCandidateBundle enrichCandidatesWithClickCounts(GuideCandidateBundle bundle) {
        if (bundle == null) {
            return new GuideCandidateBundle(List.of(), List.of());
        }
        return new GuideCandidateBundle(
                enrichList(bundle.candidates()),
                enrichList(bundle.unfilteredCandidates())
        );
    }

    private List<GuideRecommendRequest.GuideCandidateDto> enrichList(List<GuideRecommendRequest.GuideCandidateDto> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<GuideRecommendRequest.GuideCandidateDto> out = new ArrayList<>(in.size());
        for (GuideRecommendRequest.GuideCandidateDto c : in) {
            if (c == null || c.getGuideId() == null) {
                out.add(c);
                continue;
            }
            int clicks = clickStore.recentClickCount(c.getGuideId());
            out.add(GuideRecommendRequest.GuideCandidateDto.builder()
                    .guideId(c.getGuideId())
                    .guideName(c.getGuideName())
                    .region(c.getRegion())
                    .guideStyle(c.getGuideStyle())
                    .priceLevel(c.getPriceLevel())
                    .specialtyTags(c.getSpecialtyTags())
                    .languages(c.getLanguages())
                    .averageRating(c.getAverageRating())
                    .reviewCount(c.getReviewCount())
                    .approvedRefundCount(c.getApprovedRefundCount())
                    .matchRequestCount(c.getMatchRequestCount())
                    .progressedMatchCount(c.getProgressedMatchCount())
                    .chatStartCount(c.getChatStartCount())
                    .representativeImageUrl(c.getRepresentativeImageUrl())
                    .publicFeedThumbnailUrls(c.getPublicFeedThumbnailUrls())
                    .recommendClickCount(clicks)
                    .build());
        }
        return out;
    }

    private static int promptHash(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 0;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(prompt.getBytes(StandardCharsets.UTF_8));
        long v = crc32.getValue();
        return (int) (v ^ (v >>> 32));
    }

    private static GuideRecommendResponse enrichNoticeForDateFilteredEmpty(
            PromptRecommendApiRequest request,
            GuideCandidateBundle bundle,
            LocalDate from,
            LocalDate to,
            GuideRecommendResponse body
    ) {
        if (body == null || body.getTotalCount() > 0) {
            return body;
        }
        if (from == null || to == null) {
            return body;
        }
        if (bundle == null || bundle.candidates() == null || bundle.unfilteredCandidates() == null) {
            return body;
        }
        if (!bundle.candidates().isEmpty()) {
            return body;
        }
        if (bundle.unfilteredCandidates().isEmpty()) {
            return body;
        }
        String extra = "선택한 날짜에 가능한 가이드가 적어 추천이 비었어요. 날짜를 바꾸거나 기간을 넓혀보세요.";
        String notice = (body.getNotice() == null || body.getNotice().isBlank())
                ? extra
                : extra + " " + body.getNotice();

        List<String> mergedCodes = new ArrayList<>();
        mergedCodes.add(com.team6.module.ai.support.RecommendationNoticeCodes.DATE_FILTERED_NO_AVAILABLE);
        if (body.getNoticeCodes() != null) {
            mergedCodes.addAll(body.getNoticeCodes());
        }

        return GuideRecommendResponse.builder()
                .conceptSummary(body.getConceptSummary())
                .keywords(body.getKeywords())
                .matchRequestDraft(body.getMatchRequestDraft())
                .notice(notice)
                .noticeCodes(mergedCodes)
                .policyVersion(body.getPolicyVersion())
                .promptParseConfidence(body.getPromptParseConfidence())
                .totalCount(body.getTotalCount())
                .recommendations(body.getRecommendations())
                .specialSuggestion(body.getSpecialSuggestion())
                .build();
    }

    private static LocalDate resolveDesiredFrom(PromptRecommendApiRequest request) {
        // 프론트가 시작일/단일 희망일을 명시적으로 보냈다면 그 값을 우선 신뢰한다.
        if (request.getDesiredTourDateFrom() != null) {
            return request.getDesiredTourDateFrom();
        }
        if (request.getDesiredTourDate() != null) {
            return request.getDesiredTourDate();
        }
        return null;
    }

    private static LocalDate resolveDesiredTo(PromptRecommendApiRequest request, LocalDate from) {
        // 종료일이 따로 없으면 단일 날짜 요청으로 간주해 from과 동일하게 본다.
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
        // 명시 필드가 비어 있으면 프롬프트에서 날짜 범위를 뽑아본다.
        // 이 로직 덕분에 "4/20~4/22 가능한 가이드" 같은 문장도 일정 필터에 걸 수 있다.
        PromptParser.DesiredDateRange r = promptParser.extractDesiredTourDateRange(request.getPrompt());
        return r == null ? null : r.from();
    }

    private LocalDate resolveDesiredToWithPromptFallback(PromptRecommendApiRequest request, LocalDate from) {
        LocalDate to = resolveDesiredTo(request, from);
        if (to != null) {
            return to;
        }
        // 시작일과 마찬가지로, 종료일도 프롬프트 해석 결과를 fallback으로 사용한다.
        PromptParser.DesiredDateRange r = promptParser.extractDesiredTourDateRange(request.getPrompt());
        return r == null ? null : r.to();
    }

    private GuideRecommendResponse.SpecialSuggestion buildSpecialSuggestionIfNeeded(
            PromptRecommendApiRequest request,
            GuideRecommendResponse main,
            GuideCandidateBundle bundle,
            LocalDate from,
            LocalDate to,
            Map<Long, Integer> availabilityDays
    ) {
        if (bundle == null || bundle.unfilteredCandidates() == null || bundle.unfilteredCandidates().isEmpty()) {
            return null;
        }

        // 메인 추천이 아예 비었더라도, 일정 필터만 없었다면 추천될 가이드가 있는지 한 번 더 확인한다.
        // 즉 "추천 불가"와 "일정만 안 맞아서 빠짐"을 구분해서 보여주려는 의도다.
        if (main == null || main.getRecommendations() == null || main.getRecommendations().isEmpty()) {
            return computeTop1SpecialSuggestion(request, bundle, from, to, availabilityDays);
        }

        Long mainTop1 = main.getRecommendations().get(0).getGuideId();
        GuideRecommendResponse.SpecialSuggestion special = computeTop1SpecialSuggestion(request, bundle, from, to, availabilityDays);
        if (special == null || special.getGuide() == null || special.getGuide().getGuideId() == null) {
            return null;
        }
        Long unfilteredTop1 = special.getGuide().getGuideId();
        // 정책: 일정 필터 때문에 메인 Top1이 달라진 경우에만 특별 제시한다.
        // 메인 Top1과 원본 Top1이 같으면 굳이 별도 specialSuggestion을 만들 필요가 없다.
        return Objects.equals(mainTop1, unfilteredTop1) ? null : special;
    }

    private GuideRecommendResponse.SpecialSuggestion computeTop1SpecialSuggestion(
            PromptRecommendApiRequest request,
            GuideCandidateBundle bundle,
            LocalDate from,
            LocalDate to,
            Map<Long, Integer> availabilityDays
    ) {
        // 메인 후보 필터를 적용하지 않은 원본 후보군으로 "진짜 Top1"을 다시 계산한다.
        // 이때 topN을 1로 고정해서 "일정 필터만 없었다면 가장 먼저 추천됐을 가이드"를 찾는다.
        GuideRecommendResponse top1 = promptRecommendationService.recommendByPrompt(
                request.getPrompt(),
                1,
                bundle.unfilteredCandidates(),
                availabilityDays == null || availabilityDays.isEmpty() ? null : availabilityDays
        );
        if (top1.getRecommendations() == null || top1.getRecommendations().isEmpty()) {
            return null;
        }
        GuideRecommendItem guide = top1.getRecommendations().get(0);
        String notice = buildAvailabilityNotice(guide.getGuideId(), from, to);
        return GuideRecommendResponse.SpecialSuggestion.builder()
                .guide(guide)
                .notice(notice)
                .build();
    }

    private String buildAvailabilityNotice(Long guideId, LocalDate from, LocalDate to) {
        String base = "조건에 잘 부합하지만 선택한 날짜에는 예약이 있어요";
        if (guideId == null || from == null) {
            return base;
        }

        // 특별 제시 카드에는 "이 날짜는 불가하지만 기간 내 가능한 날짜가 언제인지"를 함께 안내한다.
        // 사용자는 이 안내를 보고 날짜를 바꾸거나, 다른 추천을 선택할지 판단할 수 있다.
        List<LocalDate> available = guideAvailabilityProvider.availableDates(guideId, from, to);
        if (available == null || available.isEmpty()) {
            return base;
        }
        // 안내는 과도하지 않게 7개까지만 노출한다.
        List<LocalDate> clipped = new ArrayList<>();
        for (int i = 0; i < available.size() && i < 7; i++) {
            clipped.add(available.get(i));
        }
        String suffix = " (기간 내 가능: " + String.join(", ", clipped.stream().map(LocalDate::toString).toList()) + ")";
        return base + suffix;
    }

    /**
     * 희망 기간이 있을 때 후보별 예약 가능 일수를 세어, 랭킹 가산에 넘긴다.
     */
    private Map<Long, Integer> buildAvailabilityDayCounts(
            GuideCandidateBundle bundle,
            LocalDate from,
            LocalDate to
    ) {
        Map<Long, Integer> out = new HashMap<>();
        if (from == null || to == null || bundle == null || bundle.candidates() == null) {
            return out;
        }
        for (GuideRecommendRequest.GuideCandidateDto c : bundle.candidates()) {
            if (c == null || c.getGuideId() == null) {
                continue;
            }
            List<LocalDate> av = guideAvailabilityProvider.availableDates(c.getGuideId(), from, to);
            out.put(c.getGuideId(), av == null ? 0 : av.size());
        }
        return out;
    }
}
