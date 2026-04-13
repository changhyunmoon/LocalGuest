package com.team6.module.ai.regression;

import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.support.AdjacentRegionProvider;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.engine.MatchingEngine;
import com.team6.module.ai.engine.ReasonGenerator;
import com.team6.module.ai.engine.ScoreCalculator;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.policy.ActivityMatchPolicy;
import com.team6.module.ai.policy.BudgetMatchPolicy;
import com.team6.module.ai.policy.FeedbackMatchPolicy;
import com.team6.module.ai.policy.LanguageMatchPolicy;
import com.team6.module.ai.policy.RegionMatchPolicy;
import com.team6.module.ai.policy.StyleMatchPolicy;
import com.team6.module.ai.service.AiRecommendationService;
import com.team6.module.ai.service.AiRecommendationServiceImpl;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendationTuning;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 룰 기반 AI 추천의 회귀(품질 퇴보)를 빠르게 감지하기 위한 테스트.
 * <ul>
 *   <li>프롬프트 파싱 → 추천 엔진까지 end-to-end로 검증</li>
 *   <li>과도하게 빡센 기대값 대신 Top1 안정성·근거 필드 유효성 위주</li>
 * </ul>
 * <p><b>유지보수</b>: 스코어·Reason·응답 계약을 바꾼 PR에서는
 * {@link com.team6.module.ai.support.AiRecommendationTuning#POLICY_VERSION} 상향 여부를 검토하고,
 * 이 클래스의 시나리오·별도 엣지 테스트({@code regression_*})를 함께 갱신한다.
 */
class AiRecommendationRegressionTest {

    private final PromptParser promptParser = new PromptParser(new LocalGuestAiProperties());
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
            if (s.expectedReasonCode() != null) {
                assertThat(top1.getReasonCodes()).as("reasonCodes should include expected code for prompt: %s".formatted(s.prompt()))
                        .contains(s.expectedReasonCode());
            }
        }
    }

    @Test
    void regression_adjacent_region_when_pool_lacks_exact_region() {
        List<GuideRecommendRequest.GuideCandidateDto> pool = List.of(
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(70L)
                        .guideName("속초인접")
                        .region("속초")
                        .guideStyle("힐링")
                        .priceLevel("낮음")
                        .specialtyTags(List.of("산책", "바다", "카페"))
                        .languages(List.of("한국어"))
                        .build(),
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(71L)
                        .guideName("부산비교")
                        .region("부산")
                        .guideStyle("힐링")
                        .priceLevel("낮음")
                        .specialtyTags(List.of("산책", "바다"))
                        .languages(List.of("한국어"))
                        .build()
        );
        GuideRecommendRequest parsed = promptParser.parse("강릉 힐링 산책 바다", 2, pool);
        GuideRecommendResponse response = aiRecommendationService.recommend(parsed);

        assertThat(response.getRecommendations()).isNotEmpty();
        var top1 = response.getRecommendations().get(0);
        assertThat(top1.getGuideId()).isEqualTo(70L);
        assertThat(top1.getMatched().isRegion()).isFalse();
        assertThat(top1.getMatched().isRegionAdjacent()).isTrue();
        assertThat(top1.getReasonCodes()).contains(ReasonGenerator.CODE_REGION_ADJACENT);
    }

    @Test
    void regression_budget_adjacent_reason_on_tier_gap() {
        List<GuideRecommendRequest.GuideCandidateDto> pool = List.of(
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(80L)
                        .guideName("부산가이드")
                        .region("부산")
                        .guideStyle("감성")
                        .priceLevel("중간")
                        .specialtyTags(List.of("카페", "야경"))
                        .languages(List.of("한국어"))
                        .build()
        );
        GuideRecommendRequest parsed = promptParser.parse("부산 가성비 저렴 감성 카페", 1, pool);
        GuideRecommendResponse response = aiRecommendationService.recommend(parsed);

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getGuideId()).isEqualTo(80L);
        assertThat(response.getRecommendations().get(0).getReasonCodes())
                .contains(ReasonGenerator.CODE_BUDGET_ADJACENT);
    }

    @Test
    void regression_policy_version_echoes_tuning_constant() {
        GuideRecommendRequest parsed = promptParser.parse(
                "부산 감성 카페",
                1,
                List.of(defaultCandidates().get(0))
        );
        GuideRecommendResponse response = aiRecommendationService.recommend(parsed);

        assertThat(response.getPolicyVersion()).isEqualTo(AiRecommendationTuning.POLICY_VERSION);
    }

    private List<Scenario> regressionScenarios() {
        return List.of(
                scenario("부산 감성 카페 여행하고 싶어요", 3, 1L, "카페", null),
                scenario("부산 야경 맛집 위주로 추천", 3, 1L, "야경", null),
                scenario("제주 감성 오션뷰(바다) 필수!", 3, 2L, "바다", null),
                scenario("Jeju trip 오션뷰 바다 감성으로 추천", 3, 2L, "바다", null),
                scenario("제주 식도락(맛집) + 카페", 3, 2L, "맛집", null),
                scenario("서울 액티비티 등산 트레킹 하고 싶어요", 3, 3L, "등산", null),
                scenario("서울 쇼핑 + 맛집 코스", 3, 3L, "쇼핑", null),
                scenario("강릉 힐링 산책 바다", 3, 4L, "산책", null),
                scenario("강릉 조용히 바다 산책하고 싶어요", 3, 4L, "바다", null),
                scenario("제주 여행 20만원 정도, 카페+바다", 3, 2L, "카페", null),
                scenario("부산 여행 예산 8만원인데 가성비 카페", 3, 1L, "카페", null),
                scenario("서울 50만원까지 가능 럭셔리 쇼핑", 3, 3L, "쇼핑", null),
                scenario("부산 혼자 여행, 영어 가능한 가이드", 3, 1L, null, "영어"),
                scenario("제주 가족 여행, 한국어 가이드", 3, 2L, null, "한국어"),
                scenario("서울 친구랑 액티비티, 영어도 되면 좋음", 3, 3L, null, "영어"),
                scenario("강릉 2박3일 4명 힐링 산책", 3, 4L, "산책", null),
                scenario("제주 3일 2명 여행 오션뷰", 3, 2L, "바다", null),
                scenario("제주 2박3일 4명 여행인데 술집은 빼고 카페 바다", 3, 2L, "카페", null),
                scenario("부산 감성 사진 인생샷 카페", 3, 1L, "사진", null),
                scenario("서울 전시 미술관 박물관", 3, 3L, "전시", null),
                scenario("제주 시장 로컬 골목 여행", 3, 5L, "시장", null),
                scenario("부산 브런치 감성 카페 추천해줘", 3, 1L, "카페", null),
                scenario("제주 주말 바다 해수욕장 위주로", 3, 2L, "바다", null),
                scenario("강릉 일몰 노을 바다 산책 코스", 3, 4L, "바다", null),
                scenario("서울 아울렛 럭셔리 쇼핑 영어 가능한 가이드", 3, 3L, "쇼핑", "영어"),
                scenario(
                        "서울 맛집 쇼핑 위주인데 복잡한 쇼핑몰 코스는 부담이에요",
                        3,
                        3L,
                        "맛집",
                        null,
                        ReasonGenerator.CODE_SOFT_ACTIVITY_PENALTY
                ),
                scenario("부산 약 25만 이내 감성 카페 여행", 3, 1L, "카페", null),
                scenario("제주 워크샵 단체로 맛집 위주", 3, 2L, "맛집", null)
        );
    }

    private static Scenario scenario(String prompt, int topN, Long expectedTop1GuideId, String expectedTag, String expectedLanguage) {
        return new Scenario(prompt, topN, expectedTop1GuideId, expectedTag, expectedLanguage, null);
    }

    private static Scenario scenario(
            String prompt,
            int topN,
            Long expectedTop1GuideId,
            String expectedTag,
            String expectedLanguage,
            String expectedReasonCode
    ) {
        return new Scenario(prompt, topN, expectedTop1GuideId, expectedTag, expectedLanguage, expectedReasonCode);
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
        LocalGuestAiProperties aiProps = new LocalGuestAiProperties();
        AdjacentRegionProvider adjacent = new AdjacentRegionProvider(aiProps);
        ScoreCalculator scoreCalculator = new ScoreCalculator(
                new RegionMatchPolicy(adjacent),
                new StyleMatchPolicy(),
                new BudgetMatchPolicy(),
                new ActivityMatchPolicy(),
                new LanguageMatchPolicy(),
                new FeedbackMatchPolicy(),
                new AiRecommendationMetrics(new SimpleMeterRegistry())
        );
        ReasonGenerator reasonGenerator = new ReasonGenerator(adjacent);

        return new AiRecommendationServiceImpl(new MatchingEngine(scoreCalculator, reasonGenerator, adjacent));
    }

    private record Scenario(
            String prompt,
            int topN,
            Long expectedTop1GuideId,
            String expectedTag,
            String expectedLanguage,
            String expectedReasonCode
    ) {
    }
}

