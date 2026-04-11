package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.parser.PromptParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbBackedGuideCandidateProviderTest {

    @Mock
    private GuideProfileRepository guideProfileRepository;

    private DbBackedGuideCandidateProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DbBackedGuideCandidateProvider(
                guideProfileRepository,
                new PromptParser(new LocalGuestAiProperties())
        );
    }

    @Test
    void returnsClientCandidatesWhenNonEmpty() {
        List<GuideRecommendRequest.GuideCandidateDto> client = List.of(
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(9L)
                        .guideName("테스트")
                        .region("서울")
                        .guideStyle("힐링")
                        .priceLevel("중간")
                        .specialtyTags(List.of("카페"))
                        .languages(List.of("한국어"))
                        .build()
        );
        List<GuideRecommendRequest.GuideCandidateDto> out =
                provider.getCandidates("제주 2박", 3, client);
        assertThat(out).isSameAs(client);
        verify(guideProfileRepository, never()).findByIsApprovedTrueAndIsActiveTrue(any(Pageable.class));
    }

    @Test
    void loadsFromDbWhenCandidatesEmptyAndRegionPresent() {
        GuideProfile p = GuideProfile.builder()
                .id(1L)
                .memberId(10L)
                .nickname("가이드A")
                .region("제주")
                .language("한국어, English")
                .pricePerHour(new BigDecimal("40000"))
                .isApproved(true)
                .isActive(true)
                .build();
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(p)));

        List<GuideRecommendRequest.GuideCandidateDto> out =
                provider.getCandidates("제주에서 힐링하고 싶어요", 3, List.of());

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getGuideId()).isEqualTo(1L);
        assertThat(out.get(0).getRegion()).isEqualTo("제주");
        assertThat(out.get(0).getLanguages()).containsExactly("한국어", "English");
        assertThat(out.get(0).getPriceLevel()).isEqualTo("낮음");
    }

    @Test
    void fallsBackToGlobalPoolWhenRegionQueryEmpty() {
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));
        GuideProfile p = GuideProfile.builder()
                .id(2L)
                .memberId(11L)
                .nickname("가이드B")
                .region("서울")
                .language("한국어")
                .pricePerHour(new BigDecimal("80000"))
                .isApproved(true)
                .isActive(true)
                .build();
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrue(PageRequest.of(0, DbBackedGuideCandidateProvider.MAX_SERVER_CANDIDATES)))
                .thenReturn(new PageImpl<>(List.of(p)));

        List<GuideRecommendRequest.GuideCandidateDto> out =
                provider.getCandidates("제주 가고 싶어요", 3, null);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getGuideId()).isEqualTo(2L);
        verify(guideProfileRepository).findByIsApprovedTrueAndIsActiveTrue(any(Pageable.class));
    }

    @Test
    void loadsGlobalPoolWhenNoRegionInPrompt() {
        GuideProfile p = GuideProfile.builder()
                .id(3L)
                .memberId(12L)
                .nickname("가이드C")
                .region("부산")
                .language(null)
                .pricePerHour(null)
                .isApproved(true)
                .isActive(true)
                .build();
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        List<GuideRecommendRequest.GuideCandidateDto> out =
                provider.getCandidates("그냥 여행 가고 싶어요", 3, List.of());

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getGuideId()).isEqualTo(3L);
        verify(guideProfileRepository, never()).findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(
                any(),
                any(Pageable.class)
        );
    }
}
