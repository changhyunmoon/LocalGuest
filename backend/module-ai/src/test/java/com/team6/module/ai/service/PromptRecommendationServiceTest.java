package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.engine.MatchingEngine;
import com.team6.module.ai.engine.ReasonGenerator;
import com.team6.module.ai.engine.ScoreCalculator;
import com.team6.module.ai.config.DiversityRerankSnapshot;
import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.ComboMatchPolicy;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.RecommendationNoticeCodes;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.FeedbackMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import com.team6.module.ai.spi.LlmPromptExtractor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptRecommendationServiceTest {

    private PromptRecommendationService createService() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LocalGuestAiProperties aiProps = new LocalGuestAiProperties();
        ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(aiProps);
        AiRecommendationMetrics metrics = new AiRecommendationMetrics(meterRegistry);
        ScoreCalculator scoreCalculator = new ScoreCalculator(
                new RegionMatchPolicy(adjacent, scoring),
                new StyleMatchPolicy(scoring),
                new BudgetMatchPolicy(scoring),
                new ActivityMatchPolicy(scoring),
                new LanguageMatchPolicy(scoring),
                new FeedbackMatchPolicy(scoring),
                new ComboMatchPolicy(scoring),
                metrics
        );
        ReasonGenerator reasonGenerator = new ReasonGenerator(adjacent, scoring);
        AiRecommendationService aiRecommendationService =
                new AiRecommendationServiceImpl(
                        new MatchingEngine(scoreCalculator, reasonGenerator, adjacent, DiversityRerankSnapshot.defaults(),
                                metrics, scoring));

        return new PromptRecommendationService(
                new PromptParser(aiProps),
                aiRecommendationService,
                adjacent,
                metrics,
                scoring,
                aiProps,
                null,
                null
        );
    }

    @Test
    void recommendByPrompt_should_return_conceptSummary_and_keywords() {
        PromptRecommendationService service = createService();

        GuideRecommendResponse response = service.recommendByPrompt(
                "제주 2박3일 4명 여행인데 오션뷰랑 맛집 위주로 추천해줘. 술집은 빼고!",
                3,
                List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("제주바다")
                                .region("제주")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("바다", "맛집", "카페"))
                                .languages(List.of("한국어"))
                                .build()
                )
        );

        assertThat(response.getConceptSummary()).isNotBlank();
        assertThat(response.getKeywords()).isNotNull();
        assertThat(response.getMatchRequestDraft()).isNotNull();
        assertThat(response.getKeywords().getRegion()).isEqualTo("제주");
        assertThat(response.getKeywords().getDurationDays()).isEqualTo(3);
        assertThat(response.getKeywords().getHeadcount()).isEqualTo(4);
        assertThat(response.getKeywords().getActivityTags()).contains("바다", "맛집");
        assertThat(response.getKeywords().getExcludedActivityTags()).contains("술집");
        assertThat(response.getMatchRequestDraft().getDestination()).isEqualTo("제주");
        assertThat(response.getMatchRequestDraft().getConceptSummary()).isEqualTo(response.getConceptSummary());
        assertThat(response.getMatchRequestDraft().getConcept()).contains("제주 여행");
        assertThat(response.getMatchRequestDraft().getConcept()).contains("희망 활동");
        assertThat(response.getMatchRequestDraft().getConcept()).contains("맛집");
        assertThat(response.getMatchRequestDraft().getConcept()).contains("바다");
        assertThat(response.getPolicyVersion()).isEqualTo(AiRecommendationTuning.POLICY_VERSION);
        assertThat(response.getNotice()).contains("한 분뿐");
        assertThat(response.getNoticeCodes()).contains(
                RecommendationNoticeCodes.SPARSE_GUIDE_POOL
        );
    }

    @Test
    void recommendByPrompt_when_region_missing_should_return_empty_and_notice() {
        PromptRecommendationService service = createService();

        GuideRecommendResponse response = service.recommendByPrompt(
                "그냥 카페 가고 싶어요",
                3,
                List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("A")
                                .region("서울")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페"))
                                .languages(List.of("한국어"))
                                .build()
                )
        );

        assertThat(response.getRecommendations()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getNotice()).contains("지역");
        assertThat(response.getMatchRequestDraft()).isNotNull();
        assertThat(response.getMatchRequestDraft().getDestination()).isNull();
        assertThat(response.getMatchRequestDraft().getConcept()).contains("희망 활동 카페");
        assertThat(response.getNoticeCodes()).containsExactly(RecommendationNoticeCodes.REGION_REQUIRED);
        assertThat(response.getPolicyVersion()).isEqualTo(AiRecommendationTuning.POLICY_VERSION);
    }

    @Test
    void mergeNotice_should_not_prefix_null() throws Exception {
        var method = PromptRecommendationService.class.getDeclaredMethod("mergeNotice", String.class, String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(null, null, "지역 외 조건 신호가 적어")).isEqualTo("지역 외 조건 신호가 적어");
    }

    @Test
    void recommendByPrompt_when_exact_region_sparse_should_expand_adjacent_and_notice() {
        PromptRecommendationService service = createService();

        GuideRecommendResponse response = service.recommendByPrompt(
                "강릉 바다 산책 힐링 여행 추천",
                3,
                List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(99L)
                                .guideName("속초힐링")
                                .region("속초")
                                .guideStyle("힐링")
                                .priceLevel("낮음")
                                .specialtyTags(List.of("바다", "산책"))
                                .languages(List.of("한국어"))
                                .build()
                )
        );

        assertThat(response.getRecommendations()).isNotEmpty();
        assertThat(response.getKeywords().getRegion()).isEqualTo("강릉");
        assertThat(response.getNotice()).contains("인접");
        assertThat(response.getNotice()).contains("한 분뿐");
        assertThat(response.getNoticeCodes()).contains(
                RecommendationNoticeCodes.ADJACENT_REGION_INCLUDED,
                RecommendationNoticeCodes.SPARSE_GUIDE_POOL
        );
        assertThat(response.getRecommendations().get(0).getGuideId()).isEqualTo(99L);
    }

    @Test
    void recommendByPrompt_whenLlmEnabled_truncatesPromptSentToExtractor() {
        LlmPromptExtractor extractor = mock(LlmPromptExtractor.class);
        when(extractor.tryExtract(anyString(), anyInt(), any())).thenReturn(Optional.empty());

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LocalGuestAiProperties aiProps = new LocalGuestAiProperties();
        aiProps.setLlmPromptExtractionEnabled(true);
        aiProps.setLlmPromptMaxChars(4);
        ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(aiProps);
        AiRecommendationMetrics metrics = new AiRecommendationMetrics(meterRegistry);
        ScoreCalculator scoreCalculator = new ScoreCalculator(
                new RegionMatchPolicy(adjacent, scoring),
                new StyleMatchPolicy(scoring),
                new BudgetMatchPolicy(scoring),
                new ActivityMatchPolicy(scoring),
                new LanguageMatchPolicy(scoring),
                new FeedbackMatchPolicy(scoring),
                new ComboMatchPolicy(scoring),
                metrics
        );
        ReasonGenerator reasonGenerator = new ReasonGenerator(adjacent, scoring);
        AiRecommendationService aiRecommendationService =
                new AiRecommendationServiceImpl(
                        new MatchingEngine(scoreCalculator, reasonGenerator, adjacent, DiversityRerankSnapshot.defaults(),
                                metrics, scoring));

        PromptRecommendationService service = new PromptRecommendationService(
                new PromptParser(aiProps),
                aiRecommendationService,
                adjacent,
                metrics,
                scoring,
                aiProps,
                extractor,
                null
        );

        service.recommendByPrompt(
                "제주도여행가고싶어",
                2,
                List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("가")
                                .region("제주")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페"))
                                .languages(List.of("한국어"))
                                .build()
                )
        );

        ArgumentCaptor<String> promptSent = ArgumentCaptor.forClass(String.class);
        verify(extractor).tryExtract(promptSent.capture(), eq(2), any());
        assertThat(promptSent.getValue()).isEqualTo("제주도여");
    }

    @Test
    void recommendByPrompt_whenLlmRankEnabled_skipsLlmPromptExtractionViaFastPath() {
        LlmPromptExtractor extractor = mock(LlmPromptExtractor.class);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LocalGuestAiProperties aiProps = new LocalGuestAiProperties();
        aiProps.setLlmPromptExtractionEnabled(true);
        aiProps.setLlmRankEnabled(true);
        ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(aiProps);
        AiRecommendationMetrics metrics = new AiRecommendationMetrics(meterRegistry);
        ScoreCalculator scoreCalculator = new ScoreCalculator(
                new RegionMatchPolicy(adjacent, scoring),
                new StyleMatchPolicy(scoring),
                new BudgetMatchPolicy(scoring),
                new ActivityMatchPolicy(scoring),
                new LanguageMatchPolicy(scoring),
                new FeedbackMatchPolicy(scoring),
                new ComboMatchPolicy(scoring),
                metrics
        );
        ReasonGenerator reasonGenerator = new ReasonGenerator(adjacent, scoring);
        AiRecommendationService aiRecommendationService =
                new AiRecommendationServiceImpl(
                        new MatchingEngine(scoreCalculator, reasonGenerator, adjacent, DiversityRerankSnapshot.defaults(),
                                metrics, scoring));

        PromptRecommendationService service = new PromptRecommendationService(
                new PromptParser(aiProps),
                aiRecommendationService,
                adjacent,
                metrics,
                scoring,
                aiProps,
                extractor,
                null
        );

        // 룰 파서만으로도 region/confidence가 충분한 케이스: LLM extractor 호출 없이 진행해야 한다.
        service.recommendByPrompt(
                "강릉 바다 근처 감성 카페 투어 가이드 추천해요",
                2,
                List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("가")
                                .region("강릉")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페"))
                                .languages(List.of("한국어"))
                                .build()
                )
        );

        verifyNoInteractions(extractor);
    }

    @Test
    void sanitizeLlmReason_requiresShortQuotationCitation() {
        // 따옴표 인용이 없으면 룰 reason으로 폴백(null)
        assertThat(PromptRecommendationService.sanitizeLlmReason(
                "부산 야경 코스에 잘 맞고 리뷰도 안정적이에요. 요청하신 제외 조건도 반영할 수 있어요."
        )).isNull();

        // 소개 기반 짧은 인용이 있으면 통과
        assertThat(PromptRecommendationService.sanitizeLlmReason(
                "소개에 “조용히 걷는 산책 코스”가 있어 감성 일정에 잘 맞습니다. 술집 위주는 제외하고 구성하겠습니다."
        )).isNotNull();
    }

    @Test
    void applyLlmGuideRank_filters_out_candidates_matching_excluded_activity_tags() {
        List<GuideRecommendRequest.GuideCandidateDto> candidates = List.of(
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(1L).guideName("A").region("부산").guideStyle("감성").priceLevel("중간")
                        .specialtyTags(List.of("바다"))
                        .languages(List.of("한국어"))
                        .llmIntroSnippet("조용한 산책 코스를 좋아해요")
                        .build(),
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(2L).guideName("B").region("부산").guideStyle("파티").priceLevel("중간")
                        .specialtyTags(List.of("술집"))
                        .languages(List.of("한국어"))
                        .llmIntroSnippet("술집 투어를 자주 안내합니다")
                        .build()
        );

        List<GuideRecommendRequest.GuideCandidateDto> filtered =
                PromptRecommendationService.filterPoolByExcludedSignals(List.of("술집"), candidates);
        assertThat(filtered.stream().map(GuideRecommendRequest.GuideCandidateDto::getGuideId).toList())
                .contains(1L)
                .doesNotContain(2L);
    }
}
