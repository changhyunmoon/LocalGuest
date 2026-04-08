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

    @Test
    void parse_should_extract_headcount_and_duration_and_exclusions() {
        String prompt = "제주 2박3일로 4명 여행인데 술집은 빼고 바다(오션뷰)랑 맛집 위주로 추천해줘";

        GuideRecommendRequest request = promptParser.parse(prompt, 3, List.of());

        assertThat(request.getRegion()).isEqualTo("제주");
        assertThat(request.getHeadcount()).isEqualTo(4);
        assertThat(request.getDurationDays()).isEqualTo(3);
        assertThat(request.getExcludedActivityTags()).contains("술집");
        assertThat(request.getActivityTags()).contains("바다", "맛집");
    }

    @Test
    void parse_should_extract_duration_for_daytrip_weekend_and_weeks() {
        GuideRecommendRequest daytrip = promptParser.parse("서울 당일치기 여행 추천", 3, List.of());
        assertThat(daytrip.getDurationDays()).isEqualTo(1);

        GuideRecommendRequest weekend = promptParser.parse("부산 주말 여행할건데 맛집 위주", 3, List.of());
        assertThat(weekend.getDurationDays()).isEqualTo(2);

        GuideRecommendRequest weeks = promptParser.parse("제주 2주 살기 느낌으로 로컬 위주", 3, List.of());
        assertThat(weeks.getDurationDays()).isEqualTo(14);
    }

    @Test
    void parse_should_extract_budget_level_from_manwon_band() {
        GuideRecommendRequest request = promptParser.parse("경주 10만원대 가성비로 부탁", 3, List.of());
        assertThat(request.getBudgetLevel()).isEqualTo("낮음");
    }

    @Test
    void parse_should_extract_companion_pet_and_exclusion_synonyms() {
        GuideRecommendRequest request = promptParser.parse("강릉 반려견이랑 가는데 술집은 싫어", 3, List.of());
        assertThat(request.getCompanionType()).isEqualTo("반려견");
        assertThat(request.getExcludedActivityTags()).contains("술집");
    }

    @Test
    void parse_should_normalize_activity_tags_via_keyword_normalizer() {
        GuideRecommendRequest request = promptParser.parse("부산 브런치랑 일몰 보고 싶어", 3, List.of());
        assertThat(request.getActivityTags()).contains("카페", "야경");
    }

    @Test
    void parse_should_extract_additional_regions() {
        assertThat(promptParser.parse("대구 야시장 맛집", 3, List.of()).getRegion()).isEqualTo("대구");
        assertThat(promptParser.parse("여수 밤바다 야경", 3, List.of()).getRegion()).isEqualTo("여수");
        assertThat(promptParser.parse("통영 케이블카", 3, List.of()).getRegion()).isEqualTo("통영");
    }

    @Test
    void parse_should_extract_extra_languages_and_normalize_surfing() {
        GuideRecommendRequest request = promptParser.parse(
                "전주 한옥마을이랑 french 가이드, 서핑도 해보고 싶어",
                3,
                List.of()
        );
        assertThat(request.getRegion()).isEqualTo("전주");
        assertThat(request.getPreferredLanguages()).contains("프랑스어");
        assertThat(request.getActivityTags()).contains("바다");
    }

    @Test
    void parse_should_extract_soft_penalty_for_heavy_hiking() {
        GuideRecommendRequest request = promptParser.parse(
                "제주 여행인데 힘든 등산 코스는 싫어요",
                3,
                List.of()
        );
        assertThat(request.getSoftPenaltyActivityTags()).contains("등산");
    }
}
