package com.team6.module.ai.parser;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptParserTest {

    private final PromptParser promptParser = new PromptParser();

    @Test
    void parse_should_extract_keywords_from_prompt() {
        String prompt = "부산에서 혼자 감성 카페 여행하고 싶고 영어 가능한 가이드면 좋겠어요";

        GuideRecommendRequest request = promptParser.parse(prompt, 3, List.of());

        assertThat(request.getRegion()).isEqualTo("부산");
        assertThat(request.getCompanionType()).isEqualTo("혼자");
        assertThat(request.getTravelStyle()).isEqualTo("감성");
        assertThat(request.getActivityTags()).contains("카페");
        assertThat(request.getPreferredLanguages()).contains("영어");
    }

    @Test
    void parse_should_extract_budget_level_from_amount() {
        String prompt = "제주 여행 20만원 정도 생각 중이고 감성 카페 가고 싶어요";

        GuideRecommendRequest request = promptParser.parse(prompt, 3, List.of());

        assertThat(request.getRegion()).isEqualTo("제주");
        assertThat(request.getBudgetLevel()).isEqualTo("중간");
        assertThat(request.getActivityTags()).contains("카페");
    }
}