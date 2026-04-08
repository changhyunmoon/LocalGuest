package com.team6.module.ai.regression;

import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.engine.MatchingEngine;
import com.team6.module.ai.engine.ReasonGenerator;
import com.team6.module.ai.engine.ScoreCalculator;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import com.team6.module.ai.service.AiRecommendationService;
import com.team6.module.ai.service.AiRecommendationServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 룰 기반 AI 추천의 회귀(품질 퇴보)를 빠르게 감지하기 위한 테스트.
 * - 프롬프트 파싱 → 추천 엔진까지 end-to-end로 검증
 * - 과도하게 빡센 기대값 대신 "Top1 안정성/근거 필드 유효성" 위주로 체크
 */
class AiRecommendationRegressionTest {

    private final PromptParser promptParser = new PromptParser();
    private final AiRecommendationService aiRecommendationService = createService();

    @Test
    void regression_scenarios_should_keep_reason_and_matched_consistent() {
        List<Scenario> scenarios = regressionScenarios();
        List<GuideRecommendRequest.GuideCandidateDto> candidates = defaultCandidates();

        for (Scenario s : scenarios) {
            GuideRecommendRequest parsed = promptParser.parse(s.prompt(), s.topN(), candidates);
            GuideRecommendResponse response = aiRecommendationService.recommend(parsed);

            assertThat(response).as("response should not be null").isNotNull();
            assertThat(response.getTotalCount()).as("totalCount should match list size").isEqualTo(response.getRecommendations().size());
            assertThat(response.getRecommendations()).as("should have recommendations").isNotEmpty();

            var top1 = response.getRecommendations().get(0);
            assertThat(top1.getGuideId()).as("top1 should match expected for prompt: %s".formatted(s.prompt()))
                    .isEqualTo(s.expectedTop1GuideId());

            assertThat(top1.getReason()).as("reason should be present").isNotBlank();
            assertThat(top1.getReasonCodes()).as("reasonCodes should be present for prompt: %s".formatted(s.prompt()))
                    .isNotNull()
                    .isNotEmpty();
            assertThat(top1.getReasonCodes().stream().filter(Objects::nonNull).noneMatch(String::isBlank))
                    .as("reasonCodes should not contain blank entries")
                    .isTrue();
            assertThat(top1.getMatched()).as("matched evidence should be present").isNotNull();

            if (s.expectedTag() != null) {
                assertThat(top1.getMatched().getTags()).as("matched tags should include expected tag")
                        .contains(s.expectedTag());
            }
            if (s.expectedLanguage() != null) {
                assertThat(top1.getMatched().getLanguages()).as("matched languages should include expected language")
                        .contains(s.expectedLanguage());
            }
        }
    }

    private List<Scenario> regressionScenarios() {
        return List.of(
                new Scenario("부산 감성 카페 여행하고 싶어요", 3, 1L, "카페", null),
                new Scenario("부산 야경 맛집 위주로 추천", 3, 1L, "야경", null),
                new Scenario("제주 감성 오션뷰(바다) 필수!", 3, 2L, "바다", null),
                new Scenario("제주 식도락(맛집) + 카페", 3, 2L, "맛집", null),
                new Scenario("서울 액티비티 등산 트레킹 하고 싶어요", 3, 3L, "등산", null),
                new Scenario("서울 쇼핑 + 맛집 코스", 3, 3L, "쇼핑", null),
                new Scenario("강릉 힐링 산책 바다", 3, 4L, "산책", null),
                new Scenario("강릉 조용히 바다 산책하고 싶어요", 3, 4L, "바다", null),
                new Scenario("제주 여행 20만원 정도, 카페+바다", 3, 2L, "카페", null),
                new Scenario("부산 여행 예산 8만원인데 가성비 카페", 3, 1L, "카페", null),
                new Scenario("서울 50만원까지 가능 럭셔리 쇼핑", 3, 3L, "쇼핑", null),
                new Scenario("부산 혼자 여행, 영어 가능한 가이드", 3, 1L, null, "영어"),
                new Scenario("제주 가족 여행, 한국어 가이드", 3, 2L, null, "한국어"),
                new Scenario("서울 친구랑 액티비티, 영어도 되면 좋음", 3, 3L, null, "영어"),
                new Scenario("강릉 2박3일 4명 힐링 산책", 3, 4L, "산책", null),
                new Scenario("제주 3일 2명 여행 오션뷰", 3, 2L, "바다", null),
                new Scenario("제주 2박3일 4명 여행인데 술집은 빼고 카페 바다", 3, 2L, "카페", null),
                new Scenario("부산 감성 사진 인생샷 카페", 3, 1L, "사진", null),
                new Scenario("서울 전시 미술관 박물관", 3, 3L, "전시", null),
                new Scenario("제주 시장 로컬 골목 여행", 3, 5L, "시장", null),
                new Scenario("부산 브런치 감성 카페 추천해줘", 3, 1L, "카페", null),
                new Scenario("제주 주말 바다 해수욕장 위주로", 3, 2L, "바다", null),
                new Scenario("강릉 일몰 노을 바다 산책 코스", 3, 4L, "바다", null),
                new Scenario("서울 아울렛 럭셔리 쇼핑 영어 가능한 가이드", 3, 3L, "쇼핑", "영어")
        );
    }

    private List<GuideRecommendRequest.GuideCandidateDto> defaultCandidates() {
        return List.of(
                // 부산 감성
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(1L)
                        .guideName("부산감성")
                        .region("부산")
                        .guideStyle("감성")
                        .priceLevel("중간")
                        .specialtyTags(List.of("카페", "야경", "사진", "맛집"))
                        .languages(List.of("한국어", "영어"))
                        .build(),
                // 제주 감성/바다
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(2L)
                        .guideName("제주바다")
                        .region("제주")
                        .guideStyle("감성")
                        .priceLevel("중간")
                        .specialtyTags(List.of("바다", "카페", "맛집", "사진"))
                        .languages(List.of("한국어"))
                        .build(),
                // 서울 액티비티/쇼핑
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(3L)
                        .guideName("서울액티비티")
                        .region("서울")
                        .guideStyle("액티비티")
                        .priceLevel("높음")
                        .specialtyTags(List.of("등산", "쇼핑", "맛집", "전시"))
                        .languages(List.of("한국어", "영어"))
                        .build(),
                // 강릉 힐링
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(4L)
                        .guideName("강릉힐링")
                        .region("강릉")
                        .guideStyle("힐링")
                        .priceLevel("낮음")
                        .specialtyTags(List.of("산책", "바다", "카페"))
                        .languages(List.of("한국어"))
                        .build(),
                // 제주 로컬/시장
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(5L)
                        .guideName("제주로컬")
                        .region("제주")
                        .guideStyle("로컬")
                        .priceLevel("낮음")
                        .specialtyTags(List.of("시장", "맛집", "산책"))
                        .languages(List.of("한국어"))
                        .build(),
                // 서울 감성 카페(비교군)
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(6L)
                        .guideName("서울감성")
                        .region("서울")
                        .guideStyle("감성")
                        .priceLevel("중간")
                        .specialtyTags(List.of("카페", "사진", "전시"))
                        .languages(List.of("한국어"))
                        .build()
        );
    }

    private AiRecommendationService createService() {
        ScoreCalculator scoreCalculator = new ScoreCalculator(
                new RegionMatchPolicy(),
                new StyleMatchPolicy(),
                new BudgetMatchPolicy(),
                new ActivityMatchPolicy(),
                new LanguageMatchPolicy()
        );

        return new AiRecommendationServiceImpl(new MatchingEngine(scoreCalculator, new ReasonGenerator()));
    }

    private record Scenario(String prompt, int topN, Long expectedTop1GuideId, String expectedTag, String expectedLanguage) {
    }
}

