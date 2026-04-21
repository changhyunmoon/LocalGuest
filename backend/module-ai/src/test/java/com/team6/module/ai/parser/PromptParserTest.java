package com.team6.module.ai.parser;

import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptParserTest {

    private final PromptParser promptParser = new PromptParser(new LocalGuestAiProperties());

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
    void extractDesiredTourDateRange_should_parse_single_and_ranges() {
        int y = LocalDate.now().getYear();

        PromptParser.DesiredDateRange single = promptParser.extractDesiredTourDateRange("4월 28일에 제주 맛집 투어");
        assertThat(single.from()).isEqualTo(LocalDate.of(y, 4, 28));
        assertThat(single.to()).isEqualTo(LocalDate.of(y, 4, 28));

        PromptParser.DesiredDateRange r1 = promptParser.extractDesiredTourDateRange("4/28~4/30 제주 맛집 투어");
        assertThat(r1.from()).isEqualTo(LocalDate.of(y, 4, 28));
        assertThat(r1.to()).isEqualTo(LocalDate.of(y, 4, 30));

        PromptParser.DesiredDateRange r2 = promptParser.extractDesiredTourDateRange("4월 28일부터 4월 30일 제주");
        assertThat(r2.from()).isEqualTo(LocalDate.of(y, 4, 28));
        assertThat(r2.to()).isEqualTo(LocalDate.of(y, 4, 30));

        PromptParser.DesiredDateRange r3 = promptParser.extractDesiredTourDateRange("4월 28일~30일 제주");
        assertThat(r3.from()).isEqualTo(LocalDate.of(y, 4, 28));
        assertThat(r3.to()).isEqualTo(LocalDate.of(y, 4, 30));
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
    void parse_should_extract_manwon_rough_budget_tier() {
        GuideRecommendRequest r = promptParser.parse("제주 8만 안으로 카페 위주", 3, List.of());
        assertThat(r.getBudgetLevel()).isEqualTo("낮음");
        assertThat(r.getRegion()).isEqualTo("제주");

        GuideRecommendRequest r2 = promptParser.parse("서울 약 25만 전후 예산", 3, List.of());
        assertThat(r2.getBudgetLevel()).isEqualTo("중간");
    }

    @Test
    void parse_should_extract_new_regions_and_camping_tag() {
        assertThat(promptParser.parse("속초 바다 산책 추천", 3, List.of()).getRegion()).isEqualTo("속초");
        assertThat(promptParser.parse("울산 문화 유적 여행", 3, List.of()).getRegion()).isEqualTo("울산");
        assertThat(promptParser.parse("양양 글램핑 가고 싶어요", 3, List.of()).getActivityTags()).contains("캠핑");
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

    @Test
    void parse_should_extract_soft_penalty_for_crowded_shopping() {
        GuideRecommendRequest request = promptParser.parse(
                "서울 맛집 쇼핑 위주인데 인파 많은 쇼핑몰은 부담",
                3,
                List.of()
        );
        assertThat(request.getSoftPenaltyActivityTags()).contains("쇼핑");
    }

    @Test
    void parse_should_resolve_region_from_english_and_romanization() {
        assertThat(promptParser.parse("Jeju cafe tour 감성", 3, List.of()).getRegion()).isEqualTo("제주");
        assertThat(promptParser.parse("Busan night food 맛집", 3, List.of()).getRegion()).isEqualTo("부산");
        assertThat(promptParser.parse("Seoul shopping day", 3, List.of()).getRegion()).isEqualTo("서울");
        assertThat(promptParser.parse("Gangneung beach walk", 3, List.of()).getRegion()).isEqualTo("강릉");
    }

    @Test
    void parse_should_pick_positive_region_when_previous_region_is_negated() {
        GuideRecommendRequest request = promptParser.parse("제주 말고 부산으로 맛집 여행", 3, List.of());
        assertThat(request.getRegion()).isEqualTo("부산");
        assertThat(request.getExcludedRegions()).contains("제주");
    }

    @Test
    void parse_should_prefer_positive_tags_over_negated_tags_in_long_prompt() {
        GuideRecommendRequest request = promptParser.parse(
                "부산에서 카페랑 바다는 좋고 술집이나 클럽은 말고, 붐비는 쇼핑몰도 싫어요",
                3,
                List.of()
        );

        assertThat(request.getActivityTags()).contains("카페", "바다");
        assertThat(request.getActivityTags()).doesNotContain("술집", "쇼핑");
        assertThat(request.getExcludedActivityTags()).contains("술집", "쇼핑");
    }

    @Test
    void parse_should_extract_budget_from_range_and_per_person_style_expressions() {
        GuideRecommendRequest request = promptParser.parse(
                "제주 여행 총 예산은 20만원~30만원 정도고 1인당 7만원쯤 생각해",
                3,
                List.of()
        );

        assertThat(request.getBudgetLevel()).isEqualTo("중간");
    }

    @Test
    void parse_should_extract_duration_from_date_range_and_nights_only() {
        GuideRecommendRequest byDate = promptParser.parse("부산 4/20~4/22 여행으로 맛집 추천", 3, List.of());
        assertThat(byDate.getDurationDays()).isEqualTo(3);

        GuideRecommendRequest byNight = promptParser.parse("제주 1박 여행으로 바다 보고 싶어", 3, List.of());
        assertThat(byNight.getDurationDays()).isEqualTo(2);
    }

    @Test
    void parse_should_pick_prioritized_style_from_complex_prompt() {
        GuideRecommendRequest request = promptParser.parse(
                "서울에서 액티비티도 조금 하고 싶지만 전체적으로는 조용하고 힐링 위주 여행 원해",
                3,
                List.of()
        );

        assertThat(request.getTravelStyle()).isEqualTo("힐링");
    }

    @Test
    void parse_should_ignore_negated_language_requirement() {
        GuideRecommendRequest request = promptParser.parse(
                "부산 여행인데 영어는 꼭 아니어도 되고 일본어 가이드면 좋겠어",
                3,
                List.of()
        );

        assertThat(request.getPreferredLanguages()).contains("일본어");
        assertThat(request.getPreferredLanguages()).doesNotContain("영어");
    }

    @Test
    void parse_should_extract_duration_from_day_range_expression() {
        GuideRecommendRequest request = promptParser.parse(
                "강릉 2~3일 정도 산책이랑 바다 중심으로 가고 싶어",
                3,
                List.of()
        );

        assertThat(request.getDurationDays()).isEqualTo(3);
        assertThat(request.getActivityTags()).contains("산책", "바다");
    }

    @Test
    void parse_should_apply_yaml_region_aliases_over_defaults() {
        LocalGuestAiProperties props = new LocalGuestAiProperties();
        props.getParser().getRegionAliases().put("jeju", "부산");
        PromptParser custom = new PromptParser(props);
        assertThat(custom.parse("Jeju 카페", 3, List.of()).getRegion()).isEqualTo("부산");
    }

    @Test
    void signals_should_detect_exclusion_intent_keywords_and_budget_duration_hints() {
        PromptParser.ParseSignals s = promptParser.signals("제주 2박3일로 예산은 20만 안으로, 술집은 말고 추천해줘");
        assertThat(s.matchedExclusionIntentKeywords()).contains("말고");
        assertThat(s.hasBudgetHint()).isTrue();
        assertThat(s.hasDurationHint()).isTrue();
    }

    @Test
    void parse_should_extract_solo_companion_and_insta_style_and_marine_activity() {
        GuideRecommendRequest r = promptParser.parse("부산 솔로 여행 인스타 핫플 카페", 3, List.of());
        assertThat(r.getCompanionType()).isEqualTo("혼자");
        assertThat(r.getTravelStyle()).isEqualTo("감성");

        GuideRecommendRequest r2 = promptParser.parse("제주 스노클링 다이빙 하고 싶어", 3, List.of());
        assertThat(r2.getRegion()).isEqualTo("제주");
        assertThat(r2.getActivityTags()).contains("바다");
    }

    @Test
    void parse_should_map_scenic_and_indoor_weather_intents_to_tags() {
        GuideRecommendRequest scenic = promptParser.parse("강릉 경치 좋은 전망 위주로 조용히 보고 싶어", 3, List.of());
        assertThat(scenic.getActivityTags()).contains("야경");
        assertThat(scenic.getTravelStyle()).isEqualTo("힐링");

        GuideRecommendRequest indoor = promptParser.parse("제주 우천이라 실내 위주로 박물관 갈래", 3, List.of());
        assertThat(indoor.getActivityTags()).contains("전시");
    }

    @Test
    void parse_should_detect_family_with_child_phrases() {
        GuideRecommendRequest r = promptParser.parse("부산 아이랑 키즈 동반으로 바다 보러", 3, List.of());
        assertThat(r.getCompanionType()).isEqualTo("가족");
        assertThat(r.getActivityTags()).contains("바다");
    }

    @Test
    void parse_should_strip_activity_when_same_tag_is_excluded_and_notice_conflict() {
        GuideRecommendRequest r = promptParser.parse("부산 맛집 빼고 맛집 위주로 부탁", 3, List.of());
        assertThat(r.getExcludedActivityTags()).contains("맛집");
        assertThat(r.getActivityTags()).doesNotContain("맛집");
        assertThat(r.getParserNoticeCodes()).contains("PROMPT_PREFERENCE_CONFLICT_RESOLVED");
    }

    @Test
    void parse_should_relax_excluded_tag_when_later_explicit_requirement() {
        GuideRecommendRequest r = promptParser.parse(
                "부산에서 처음엔 카페는 빼고 싶었는데 결국 카페 꼭 가고 싶어",
                3,
                List.of()
        );
        assertThat(r.getExcludedActivityTags()).doesNotContain("카페");
        assertThat(r.getActivityTags()).contains("카페");
        assertThat(r.getParserNoticeCodes()).contains("PROMPT_PREFERENCE_CONFLICT_RESOLVED");
    }
}
