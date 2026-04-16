package com.team6.module.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.dto.request.AiRecommendClickRequest;
import com.team6.module.ai.dto.request.PromptRecommendApiRequest;
import com.team6.module.ai.dto.response.GuideRecommendItem;
import com.team6.module.ai.dto.response.GuideRecommendResponse;
import com.team6.module.ai.http.RecommendationHttpHeaders;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.service.PromptRecommendationService;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.AiRecommendClickStore;
import com.team6.module.ai.support.GuideAvailabilityProvider;
import com.team6.module.ai.support.GuideCandidateBundle;
import com.team6.module.ai.support.GuideCandidateProvider;
import com.team6.module.ai.ModuleAiWebMvcTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.Mockito.doNothing;

/**
 * {@code POST /ai/recommend} HTTP·JSON·헤더 계약 회귀(Provider/단위 목과 보완).
 */
@SpringBootTest(classes = ModuleAiWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class AiRecommendEndpointMockMvcTest {

    private static final String PV = "2026.reg.mockmvc";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PromptRecommendationService promptRecommendationService;
    @MockitoBean
    private GuideCandidateProvider guideCandidateProvider;
    @MockitoBean
    private PromptParser promptParser;
    @MockitoBean
    private GuideAvailabilityProvider guideAvailabilityProvider;
    @MockitoBean
    private AiRecommendationMetrics recommendationMetrics;
    @MockitoBean
    private AiRecommendClickStore clickStore;

    @Test
    void postRecommend_returnsJson_andPolicyHeader() throws Exception {
        when(promptParser.extractDesiredTourDateRange(any())).thenReturn(null);
        when(clickStore.recentClickCount(any())).thenReturn(0);
        when(clickStore.debiasedClickScore(any())).thenReturn(0);

        GuideRecommendRequest.GuideCandidateDto dto = GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(7L)
                .guideName("테스트")
                .region("제주")
                .guideStyle("힐링")
                .priceLevel("중간")
                .specialtyTags(List.of("카페"))
                .languages(List.of("한국어"))
                .build();
        GuideCandidateBundle bundle = new GuideCandidateBundle(List.of(dto), List.of(dto));
        when(guideCandidateProvider.getCandidates(eq("제주"), eq(2), isNull(), isNull(), isNull()))
                .thenReturn(bundle);

        GuideRecommendItem item = GuideRecommendItem.builder()
                .guideId(7L)
                .guideName("테스트")
                .score(10)
                .reason("r")
                .reasonCodes(List.of("REGION"))
                .build();
        GuideRecommendResponse main = GuideRecommendResponse.builder()
                .policyVersion(PV)
                .totalCount(1)
                .recommendations(List.of(item))
                .build();
        when(promptRecommendationService.recommendByPrompt(eq("제주"), eq(2), anyList(), isNull(), isNull())).thenReturn(main);
        when(promptRecommendationService.recommendByPrompt(eq("제주"), eq(1), anyList(), isNull(), isNull())).thenReturn(main);

        PromptRecommendApiRequest body = new PromptRecommendApiRequest();
        body.setPrompt("제주");
        body.setTopN(2);

        mockMvc.perform(post("/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(header().string(RecommendationHttpHeaders.X_RECOMMENDATION_POLICY, PV))
                .andExpect(jsonPath("$.policyVersion").value(PV))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.recommendations[0].guideId").value(7))
                .andExpect(jsonPath("$.specialSuggestion").doesNotExist());
    }

    @Test
    void postRecommend_serializesSpecialSuggestion_whenOrchestrationProducesIt() throws Exception {
        when(promptParser.extractDesiredTourDateRange(any())).thenReturn(null);
        when(clickStore.recentClickCount(any())).thenReturn(0);
        when(clickStore.debiasedClickScore(any())).thenReturn(0);

        LocalDate d = LocalDate.of(2026, 4, 28);
        GuideRecommendRequest.GuideCandidateDto a = GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(1L).guideName("A").region("제주").guideStyle("힐링").priceLevel("중간")
                .specialtyTags(List.of("카페")).languages(List.of("한국어")).build();
        GuideRecommendRequest.GuideCandidateDto b = GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(2L).guideName("B").region("제주").guideStyle("힐링").priceLevel("중간")
                .specialtyTags(List.of("카페")).languages(List.of("한국어")).build();
        GuideCandidateBundle bundle = new GuideCandidateBundle(List.of(b), List.of(a, b));
        when(guideCandidateProvider.getCandidates(eq("제주 힐링"), anyInt(), isNull(), eq(d), eq(d)))
                .thenReturn(bundle);

        GuideRecommendItem topMain = GuideRecommendItem.builder()
                .guideId(2L).guideName("B").score(20).reason("r").reasonCodes(List.of("REGION")).build();
        GuideRecommendItem topUnfiltered = GuideRecommendItem.builder()
                .guideId(1L).guideName("A").score(30).reason("r").reasonCodes(List.of("REGION")).build();
        GuideRecommendResponse main = GuideRecommendResponse.builder()
                .policyVersion(PV).totalCount(1).recommendations(List.of(topMain)).build();
        GuideRecommendResponse unfilteredTop1 = GuideRecommendResponse.builder()
                .policyVersion(PV).totalCount(1).recommendations(List.of(topUnfiltered)).build();

        when(promptRecommendationService.recommendByPrompt(eq("제주 힐링"), eq(3), anyList(), any(), any())).thenReturn(main);
        when(promptRecommendationService.recommendByPrompt(eq("제주 힐링"), eq(1), anyList(), any(), any())).thenReturn(unfilteredTop1);
        when(guideAvailabilityProvider.availableDates(eq(2L), eq(d), eq(d))).thenReturn(List.of());
        when(guideAvailabilityProvider.availableDates(eq(1L), eq(d), eq(d))).thenReturn(List.of());

        PromptRecommendApiRequest req = new PromptRecommendApiRequest();
        req.setPrompt("제주 힐링");
        req.setTopN(3);
        req.setDesiredTourDateFrom(d);
        req.setDesiredTourDateTo(d);

        mockMvc.perform(post("/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialSuggestion.guide.guideId").value(1))
                .andExpect(jsonPath("$.specialSuggestion.notice").value("조건에 잘 부합하지만 선택한 날짜에는 예약이 있어요"));
    }

    @Test
    void postRecommendClick_returns204() throws Exception {
        doNothing().when(clickStore).recordClick(7L, 1);
        AiRecommendClickRequest body = new AiRecommendClickRequest();
        body.setGuideId(7L);
        body.setRank(1);
        body.setPolicyVersion(PV);
        body.setPrompt("제주");

        mockMvc.perform(post("/ai/recommend/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }
}
