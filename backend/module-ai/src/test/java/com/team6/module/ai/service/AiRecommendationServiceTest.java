package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.engine.MatchingEngine;
import com.team6.module.ai.engine.ReasonGenerator;
import com.team6.module.ai.engine.ScoreCalculator;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.FeedbackMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.config.DiversityRerankSnapshot;
import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.config.ScoringPolicySnapshot;
import com.team6.module.ai.policy.ComboMatchPolicy;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiRecommendationServiceTest {

    private AiRecommendationService createService() {
        LocalGuestAiProperties aiProps = new LocalGuestAiProperties();
        ScoringPolicySnapshot scoring = ScoringPolicySnapshot.defaults();
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(aiProps);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
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

        return new AiRecommendationServiceImpl(
                new MatchingEngine(scoreCalculator, reasonGenerator, adjacent, DiversityRerankSnapshot.defaults(), metrics)
        );
    }

    @Test
    void recommend_should_return_sorted_guides() {
        AiRecommendationService aiRecommendationService = createService();

        GuideRecommendRequest request = GuideRecommendRequest.builder()
                .region("부산")
                .travelStyle("감성")
                .budgetLevel("중간")
                .companionType("혼자")
                .activityTags(List.of("카페", "야경"))
                .preferredLanguages(List.of("한국어"))
                .topN(3)
                .guideCandidates(List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("A가이드")
                                .region("부산")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페", "야경"))
                                .languages(List.of("한국어"))
                                .averageRating(new BigDecimal("4.8"))
                                .reviewCount(12)
                                .representativeImageUrl("https://cdn.example.com/a.jpg")
                                .publicFeedThumbnailUrls(List.of("https://cdn.example.com/a/f1.jpg"))
                                .build(),
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(2L)
                                .guideName("B가이드")
                                .region("서울")
                                .guideStyle("액티비티")
                                .priceLevel("높음")
                                .specialtyTags(List.of("등산"))
                                .languages(List.of("영어"))
                                .build()
                ))
                .build();

        GuideRecommendResponse response = aiRecommendationService.recommend(request);

        assertThat(response).isNotNull();
        assertThat(response.getRecommendations()).isNotEmpty();
        assertThat(response.getRecommendations().get(0).getGuideId()).isEqualTo(1L);
        assertThat(response.getRecommendations().get(0).getReason()).contains("희망 지역");
        assertThat(response.getRecommendations().get(0).getReasonCodes()).contains(ReasonGenerator.CODE_REGION_MATCH);
        assertThat(response.getRecommendations().get(0).getReasonFacts()).isNotEmpty();
        assertThat(response.getRecommendations().get(0).getMatched()).isNotNull();
        assertThat(response.getRecommendations().get(0).getMatched().isRegion()).isTrue();
        assertThat(response.getRecommendations().get(0).getMatched().isRegionAdjacent()).isFalse();
        assertThat(response.getRecommendations().get(0).getRepresentativeImageUrl())
                .isEqualTo("https://cdn.example.com/a.jpg");
        assertThat(response.getRecommendations().get(0).getRegion()).isEqualTo("부산");
        assertThat(response.getRecommendations().get(0).getPriceLevel()).isEqualTo("중간");
        assertThat(response.getRecommendations().get(0).getAverageRating()).isEqualByComparingTo("4.8");
        assertThat(response.getRecommendations().get(0).getReviewCount()).isEqualTo(12);
        assertThat(response.getRecommendations().get(0).getPublicFeedThumbnailUrls())
                .containsExactly("https://cdn.example.com/a/f1.jpg");
        assertThat(response.getPolicyVersion()).isEqualTo(AiRecommendationTuning.POLICY_VERSION);
    }

    @Test
    void recommend_adjacent_region_should_add_partial_score_and_matched_flag() {
        AiRecommendationService aiRecommendationService = createService();

        GuideRecommendRequest request = GuideRecommendRequest.builder()
                .region("강릉")
                .travelStyle("힐링")
                .budgetLevel("낮음")
                .activityTags(List.of("산책", "바다"))
                .preferredLanguages(List.of("한국어"))
                .topN(1)
                .guideCandidates(List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(10L)
                                .guideName("속초힐링")
                                .region("속초")
                                .guideStyle("힐링")
                                .priceLevel("낮음")
                                .specialtyTags(List.of("산책", "바다", "카페"))
                                .languages(List.of("한국어"))
                                .build()
                ))
                .build();

        GuideRecommendResponse response = aiRecommendationService.recommend(request);

        assertThat(response.getRecommendations()).hasSize(1);
        var top = response.getRecommendations().get(0);
        assertThat(top.getMatched().isRegion()).isFalse();
        assertThat(top.getMatched().isRegionAdjacent()).isTrue();
        assertThat(top.getReasonCodes()).contains(ReasonGenerator.CODE_REGION_ADJACENT);
    }

    @Test
    void recommend_should_expose_soft_penalty_overlap_on_matched_evidence() {
        AiRecommendationService aiRecommendationService = createService();

        GuideRecommendRequest request = GuideRecommendRequest.builder()
                .region("서울")
                .travelStyle("액티비티")
                .budgetLevel("중간")
                .activityTags(List.of("맛집"))
                .softPenaltyActivityTags(List.of("쇼핑"))
                .topN(1)
                .guideCandidates(List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("A")
                                .region("서울")
                                .guideStyle("액티비티")
                                .priceLevel("중간")
                                .specialtyTags(List.of("맛집", "쇼핑"))
                                .languages(List.of("한국어"))
                                .build()
                ))
                .build();

        GuideRecommendResponse response = aiRecommendationService.recommend(request);
        assertThat(response.getRecommendations()).isNotEmpty();
        assertThat(response.getRecommendations().get(0).getMatched().getSoftPenaltyOverlapTags())
                .containsExactly("쇼핑");
    }

    @Test
    void recommend_should_set_matched_budgetAdjacent_when_budget_tier_adjacent_only() {
        AiRecommendationService aiRecommendationService = createService();

        GuideRecommendRequest request = GuideRecommendRequest.builder()
                .region("부산")
                .travelStyle("감성")
                .budgetLevel("낮음")
                .activityTags(List.of("카페"))
                .topN(1)
                .guideCandidates(List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("A")
                                .region("부산")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페"))
                                .languages(List.of("한국어"))
                                .build()
                ))
                .build();

        GuideRecommendResponse response = aiRecommendationService.recommend(request);
        var matched = response.getRecommendations().get(0).getMatched();
        assertThat(matched.isBudget()).isFalse();
        assertThat(matched.isBudgetAdjacent()).isTrue();
    }

    @Test
    void recommend_should_apply_synonym_matching_for_activity_tags() {
        AiRecommendationService aiRecommendationService = createService();

        GuideRecommendRequest request = GuideRecommendRequest.builder()
                .region("제주")
                .travelStyle("감성")
                .budgetLevel("중간")
                .activityTags(List.of("오션뷰")) // synonym -> 바다
                .topN(1)
                .guideCandidates(List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("A가이드")
                                .region("제주")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("바다"))
                                .languages(List.of("한국어"))
                                .build()
                ))
                .build();

        GuideRecommendResponse response = aiRecommendationService.recommend(request);
        assertThat(response.getRecommendations()).isNotEmpty();
        assertThat(response.getRecommendations().get(0).getScore()).isGreaterThan(0);
        assertThat(response.getRecommendations().get(0).getReason()).contains("바다");
        assertThat(response.getRecommendations().get(0).getReasonCodes()).contains(ReasonGenerator.CODE_ACTIVITY_MATCH);
        assertThat(response.getRecommendations().get(0).getMatched()).isNotNull();
        assertThat(response.getRecommendations().get(0).getMatched().getTags()).contains("바다");
    }

    @Test
    void recommend_should_return_empty_when_no_candidates() {
        AiRecommendationService aiRecommendationService = createService();

        GuideRecommendRequest request = GuideRecommendRequest.builder()
                .region("부산")
                .travelStyle("감성")
                .budgetLevel("중간")
                .companionType("혼자")
                .activityTags(List.of("카페"))
                .preferredLanguages(List.of("한국어"))
                .topN(3)
                .guideCandidates(List.of())
                .build();

        GuideRecommendResponse response = aiRecommendationService.recommend(request);

        assertThat(response).isNotNull();
        assertThat(response.getRecommendations()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
    }

    @Test
    void recommend_should_limit_results_by_topN() {
        AiRecommendationService aiRecommendationService = createService();

        GuideRecommendRequest request = GuideRecommendRequest.builder()
                .region("부산")
                .travelStyle("감성")
                .budgetLevel("중간")
                .companionType("혼자")
                .activityTags(List.of("카페"))
                .preferredLanguages(List.of("한국어"))
                .topN(1)
                .guideCandidates(List.of(
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("A가이드")
                                .region("부산")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페", "야경"))
                                .languages(List.of("한국어"))
                                .build(),
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(2L)
                                .guideName("B가이드")
                                .region("부산")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페"))
                                .languages(List.of("한국어"))
                                .build()
                ))
                .build();

        GuideRecommendResponse response = aiRecommendationService.recommend(request);

        assertThat(response).isNotNull();
        assertThat(response.getRecommendations()).hasSize(1);
    }

    @Test
    void recommend_should_rerank_for_diversity_when_top_results_are_too_similar() {
        AiRecommendationService aiRecommendationService = createService();

        GuideRecommendRequest request = GuideRecommendRequest.builder()
                .region("제주")
                .budgetLevel("중간")
                .activityTags(List.of("카페", "바다"))
                .preferredLanguages(List.of("한국어"))
                .topN(2)
                .guideCandidates(List.of(
                        // A and B are very similar (same region/style/tags) but B has slightly lower base score
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(1L)
                                .guideName("A가이드")
                                .region("제주")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페", "바다"))
                                .languages(List.of("한국어"))
                                .build(),
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(2L)
                                .guideName("B가이드")
                                .region("제주")
                                .guideStyle("감성")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페", "바다"))
                                .languages(List.of("한국어"))
                                .build(),
                        // C is slightly less matched but provides diversity (different style/tags)
                        GuideRecommendRequest.GuideCandidateDto.builder()
                                .guideId(3L)
                                .guideName("C가이드")
                                .region("제주")
                                .guideStyle("로컬")
                                .priceLevel("중간")
                                .specialtyTags(List.of("카페", "바다"))
                                .languages(List.of("한국어"))
                                .build()
                ))
                .build();

        GuideRecommendResponse response = aiRecommendationService.recommend(request);

        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().get(0).getGuideId()).isEqualTo(1L);
        // Diversity rerank should prefer C over B for the second slot
        assertThat(response.getRecommendations().get(1).getGuideId()).isEqualTo(3L);
    }
}