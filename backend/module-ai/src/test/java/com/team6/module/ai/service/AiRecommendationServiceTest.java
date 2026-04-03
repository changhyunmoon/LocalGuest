package com.team6.module.ai.service;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.engine.MatchingEngine;
import com.team6.module.ai.engine.ReasonGenerator;
import com.team6.module.ai.engine.ScoreCalculator;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiRecommendationServiceTest {

    @Test
    void recommend_should_return_sorted_guides() {
        RegionMatchPolicy regionMatchPolicy = new RegionMatchPolicy();
        StyleMatchPolicy styleMatchPolicy = new StyleMatchPolicy();
        BudgetMatchPolicy budgetMatchPolicy = new BudgetMatchPolicy();
        ActivityMatchPolicy activityMatchPolicy = new ActivityMatchPolicy();
        LanguageMatchPolicy languageMatchPolicy = new LanguageMatchPolicy();

        ScoreCalculator scoreCalculator = new ScoreCalculator(
                regionMatchPolicy,
                styleMatchPolicy,
                budgetMatchPolicy,
                activityMatchPolicy,
                languageMatchPolicy
        );

        ReasonGenerator reasonGenerator = new ReasonGenerator();
        MatchingEngine matchingEngine = new MatchingEngine(scoreCalculator, reasonGenerator);
        AiRecommendationService aiRecommendationService = new AiRecommendationServiceImpl(matchingEngine);

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
    }
    @Test
    void recommend_should_return_empty_when_no_candidates() {
        RegionMatchPolicy regionMatchPolicy = new RegionMatchPolicy();
        StyleMatchPolicy styleMatchPolicy = new StyleMatchPolicy();
        BudgetMatchPolicy budgetMatchPolicy = new BudgetMatchPolicy();
        ActivityMatchPolicy activityMatchPolicy = new ActivityMatchPolicy();
        LanguageMatchPolicy languageMatchPolicy = new LanguageMatchPolicy();

        ScoreCalculator scoreCalculator = new ScoreCalculator(
                regionMatchPolicy,
                styleMatchPolicy,
                budgetMatchPolicy,
                activityMatchPolicy,
                languageMatchPolicy
        );

        ReasonGenerator reasonGenerator = new ReasonGenerator();
        MatchingEngine matchingEngine = new MatchingEngine(scoreCalculator, reasonGenerator);
        AiRecommendationService aiRecommendationService = new AiRecommendationServiceImpl(matchingEngine);

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
}