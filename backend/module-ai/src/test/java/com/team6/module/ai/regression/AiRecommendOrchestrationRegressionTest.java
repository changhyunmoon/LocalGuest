package com.team6.module.ai.regression;

import com.team6.module.ai.controller.AiController;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.request.PromptRecommendApiRequest;
import com.team6.module.ai.dto.response.GuideRecommendItem;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.service.PromptRecommendationService;
import com.team6.module.ai.support.GuideAvailabilityProvider;
import com.team6.module.ai.support.GuideCandidateBundle;
import com.team6.module.ai.support.GuideCandidateProvider;
import com.team6.module.ai.support.AiRecommendationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiController} 기준으로 기간·후보 번들(필터/비필터)·특별 제시 계약을 회귀한다.
 * DB 없이 목으로 고정해, 스코어 룰 변경과 무관하게 “조립 정책”보만 잡는다.
 */
@ExtendWith(MockitoExtension.class)
class AiRecommendOrchestrationRegressionTest {

    private static final String PV = "regression-test-policy";

    @Mock
    private PromptRecommendationService promptRecommendationService;
    @Mock
    private GuideCandidateProvider guideCandidateProvider;
    @Mock
    private PromptParser promptParser;
    @Mock
    private GuideAvailabilityProvider guideAvailabilityProvider;

    private AiController aiController;

    @BeforeEach
    void setUp() {
        AiRecommendationMetrics metrics = new AiRecommendationMetrics(new SimpleMeterRegistry());
        aiController = new AiController(
                promptRecommendationService,
                guideCandidateProvider,
                promptParser,
                guideAvailabilityProvider,
                metrics
        );
    }

    @Test
    void regression_special_suggestion_when_schedule_filtered_top1_differs() {
        LocalDate from = LocalDate.of(2026, 4, 28);
        LocalDate to = LocalDate.of(2026, 4, 30);
        PromptRecommendApiRequest request = baseRequest(from, to);

        GuideRecommendRequest.GuideCandidateDto guideA = candidate(1L, "가이드A");
        GuideRecommendRequest.GuideCandidateDto guideB = candidate(2L, "가이드B");
        GuideCandidateBundle bundle = new GuideCandidateBundle(List.of(guideB), List.of(guideA, guideB));

        when(guideCandidateProvider.getCandidates(
                eq("제주 힐링"),
                eq(3),
                isNull(),
                eq(from),
                eq(to)
        )).thenReturn(bundle);

        GuideRecommendItem topMain = item(2L, "가이드B");
        GuideRecommendItem topUnfiltered = item(1L, "가이드A");

        when(promptRecommendationService.recommendByPrompt(eq("제주 힐링"), eq(3), any(), any()))
                .thenReturn(responseWithRecommendations(List.of(topMain)));
        when(promptRecommendationService.recommendByPrompt(eq("제주 힐링"), eq(1), any(), any()))
                .thenReturn(responseWithRecommendations(List.of(topUnfiltered)));

        when(guideAvailabilityProvider.availableDates(eq(2L), eq(from), eq(to)))
                .thenReturn(List.of());
        when(guideAvailabilityProvider.availableDates(1L, from, to))
                .thenReturn(List.of(LocalDate.of(2026, 4, 29)));

        ResponseEntity<GuideRecommendResponse> res = aiController.recommend(request);

        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getSpecialSuggestion()).isNotNull();
        assertThat(res.getBody().getSpecialSuggestion().getGuide().getGuideId()).isEqualTo(1L);
        assertThat(res.getBody().getSpecialSuggestion().getNotice())
                .contains("조건에 잘 부합하지만 선택한 날짜에는 예약이 있어요");
        assertThat(res.getBody().getSpecialSuggestion().getNotice()).contains("기간 내 가능:");
        assertThat(res.getBody().getSpecialSuggestion().getNotice()).contains("2026-04-29");
    }

    @Test
    void regression_no_special_suggestion_when_filtered_and_unfiltered_top1_same() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);
        PromptRecommendApiRequest request = baseRequest(from, to);
        request.setPrompt("부산 맛집");

        GuideRecommendRequest.GuideCandidateDto guideB = candidate(2L, "가이드B");
        GuideCandidateBundle bundle = new GuideCandidateBundle(List.of(guideB), List.of(candidate(1L, "가이드A"), guideB));

        when(guideCandidateProvider.getCandidates(eq("부산 맛집"), eq(3), isNull(), eq(from), eq(to)))
                .thenReturn(bundle);

        GuideRecommendItem topB = item(2L, "가이드B");
        when(promptRecommendationService.recommendByPrompt(eq("부산 맛집"), eq(3), any(), any()))
                .thenReturn(responseWithRecommendations(List.of(topB)));
        when(promptRecommendationService.recommendByPrompt(eq("부산 맛집"), eq(1), any(), any()))
                .thenReturn(responseWithRecommendations(List.of(topB)));

        when(guideAvailabilityProvider.availableDates(eq(2L), eq(from), eq(to))).thenReturn(List.of());

        ResponseEntity<GuideRecommendResponse> res = aiController.recommend(request);

        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getSpecialSuggestion()).isNull();
    }

    @Test
    void regression_special_suggestion_when_main_empty_but_unfiltered_has_top1() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 3);
        PromptRecommendApiRequest request = baseRequest(from, to);
        request.setPrompt("서울 야경");

        GuideRecommendRequest.GuideCandidateDto guideA = candidate(10L, "가이드10");
        GuideCandidateBundle bundle = new GuideCandidateBundle(List.of(), List.of(guideA));

        when(guideCandidateProvider.getCandidates(eq("서울 야경"), eq(3), isNull(), eq(from), eq(to)))
                .thenReturn(bundle);

        when(promptRecommendationService.recommendByPrompt(eq("서울 야경"), eq(3), any(), any()))
                .thenReturn(emptyResponse());
        when(promptRecommendationService.recommendByPrompt(eq("서울 야경"), eq(1), any(), any()))
                .thenReturn(responseWithRecommendations(List.of(item(10L, "가이드10"))));

        when(guideAvailabilityProvider.availableDates(10L, from, to)).thenReturn(List.of());

        ResponseEntity<GuideRecommendResponse> res = aiController.recommend(request);

        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getRecommendations()).isEmpty();
        assertThat(res.getBody().getSpecialSuggestion()).isNotNull();
        assertThat(res.getBody().getSpecialSuggestion().getGuide().getGuideId()).isEqualTo(10L);
    }

    @Test
    void regression_prompt_date_range_fallback_passed_to_candidate_provider() {
        PromptRecommendApiRequest request = new PromptRecommendApiRequest();
        request.setPrompt("4/28~4/30 제주도 카페");
        request.setTopN(2);

        LocalDate from = LocalDate.of(2026, 4, 28);
        LocalDate to = LocalDate.of(2026, 4, 30);
        when(promptParser.extractDesiredTourDateRange("4/28~4/30 제주도 카페"))
                .thenReturn(new PromptParser.DesiredDateRange(from, to));

        GuideCandidateBundle bundle = new GuideCandidateBundle(List.of(), List.of());
        /*
         * AiController.resolveDesiredTo: API에 desiredTourDateTo가 없으면 from과 동일 날짜로 끝난다.
         * 프롬프트 파서가 기간을 알아도, to는 resolveDesiredToWithPromptFallback에서 extract로 보강되지 않는다(현재 계약).
         */
        LocalDate expectedToSameAsFrom = from;
        when(guideCandidateProvider.getCandidates(
                eq("4/28~4/30 제주도 카페"),
                eq(2),
                isNull(),
                eq(from),
                eq(expectedToSameAsFrom)
        )).thenReturn(bundle);

        when(promptRecommendationService.recommendByPrompt(eq("4/28~4/30 제주도 카페"), eq(2), any(), any()))
                .thenReturn(emptyResponse());
        // unfiltered 후보가 비어 있으면 특별 제시 경로는 타지 않는다(AiController 초기 가드).

        aiController.recommend(request);

        ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(guideCandidateProvider).getCandidates(
                eq("4/28~4/30 제주도 카페"),
                eq(2),
                isNull(),
                fromCap.capture(),
                toCap.capture()
        );
        assertThat(fromCap.getValue()).isEqualTo(from);
        assertThat(toCap.getValue()).isEqualTo(expectedToSameAsFrom);
    }

    private static PromptRecommendApiRequest baseRequest(LocalDate from, LocalDate to) {
        PromptRecommendApiRequest request = new PromptRecommendApiRequest();
        request.setPrompt("제주 힐링");
        request.setTopN(3);
        request.setDesiredTourDateFrom(from);
        request.setDesiredTourDateTo(to);
        return request;
    }

    private static GuideRecommendRequest.GuideCandidateDto candidate(long id, String name) {
        return GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(id)
                .guideName(name)
                .region("제주")
                .guideStyle("힐링")
                .priceLevel("중간")
                .specialtyTags(List.of("카페"))
                .languages(List.of("한국어"))
                .build();
    }

    private static GuideRecommendItem item(long id, String name) {
        return GuideRecommendItem.builder()
                .guideId(id)
                .guideName(name)
                .score(100)
                .reason("test")
                .reasonCodes(List.of("REGION"))
                .build();
    }

    private static GuideRecommendResponse responseWithRecommendations(List<GuideRecommendItem> items) {
        return GuideRecommendResponse.builder()
                .policyVersion(PV)
                .totalCount(items.size())
                .recommendations(items)
                .build();
    }

    private static GuideRecommendResponse emptyResponse() {
        return GuideRecommendResponse.builder()
                .policyVersion(PV)
                .totalCount(0)
                .recommendations(List.of())
                .build();
    }
}
