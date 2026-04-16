package com.team6.integration.ai;

import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.domain.guide.repository.GuideCareerRepository;
import com.team6.domain.guide.repository.GuideFeedRepository;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.guide.repository.GuideScheduleRepository;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.entity.enums.RefundStatus;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.repository.RefundRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import com.team6.module.ai.config.LocalGuestAiProperties;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.parser.PromptParser;
import com.team6.module.ai.support.AiRecommendationMetrics;
import com.team6.module.ai.support.GuideCandidateBundle;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbBackedGuideCandidateProviderTest {

    @Mock
    private GuideProfileRepository guideProfileRepository;

    @Mock
    private GuideFeedRepository guideFeedRepository;

    @Mock
    private GuideCareerRepository guideCareerRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private MatchRequestRepository matchRequestRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private GuideScheduleRepository guideScheduleRepository;

    private DbBackedGuideCandidateProvider provider;

    @BeforeEach
    void setUp() {
        LocalGuestAiProperties aiProps = new LocalGuestAiProperties();
        aiProps.getCandidatePool().setPoolCacheTtlSeconds(0);
        AiRecommendationMetrics metrics = new AiRecommendationMetrics(new SimpleMeterRegistry());
        provider = new DbBackedGuideCandidateProvider(
                guideProfileRepository,
                guideFeedRepository,
                guideCareerRepository,
                matchRequestRepository,
                refundRepository,
                chatRoomRepository,
                guideScheduleRepository,
                new PromptParser(aiProps),
                aiProps,
                metrics
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
        GuideCandidateBundle out =
                provider.getCandidates("제주 2박", 3, client, null, null);
        assertThat(out.candidates()).isSameAs(client);
        assertThat(out.unfilteredCandidates()).isSameAs(client);
        verify(guideProfileRepository, never()).findByIsApprovedTrueAndIsActiveTrue(any(Pageable.class));
        verify(guideFeedRepository, never()).findByGuideProfile_IdInAndIsDeletedFalse(any());
        verify(guideCareerRepository, never()).findByGuideProfile_IdIn(any());
        verify(refundRepository, never()).countApprovedRefundsGroupedByGuideId(any(), any());
        verify(matchRequestRepository, never()).countAllGroupedByGuideId(any());
        verify(matchRequestRepository, never()).countByGuideIdAndStatusInGrouped(any(), any());
        verify(chatRoomRepository, never()).countRoomsGroupedByParticipantUserId(any());
        verify(guideScheduleRepository, never()).findGuideProfileIdsBookedAndPaidBetween(any(), any(), any());
    }

    @Test
    void excludesClientCandidateWhenBookedPaidOnDesiredDate() {
        LocalDate tourDate = LocalDate.of(2026, 4, 28);
        when(guideScheduleRepository.findGuideProfileIdsBookedAndPaidBetween(tourDate, tourDate, GuideScheduleStatus.BOOKED))
                .thenReturn(List.of(9L));
        List<GuideRecommendRequest.GuideCandidateDto> client = List.of(
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(9L)
                        .guideName("바쁜가이드")
                        .region("제주")
                        .guideStyle("로컬")
                        .priceLevel("중간")
                        .specialtyTags(List.of("맛집"))
                        .languages(List.of("한국어"))
                        .build(),
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(10L)
                        .guideName("여유가이드")
                        .region("제주")
                        .guideStyle("로컬")
                        .priceLevel("중간")
                        .specialtyTags(List.of("맛집"))
                        .languages(List.of("한국어"))
                        .build()
        );
        GuideCandidateBundle out =
                provider.getCandidates("제주 맛집", 3, client, tourDate, tourDate);
        assertThat(out.unfilteredCandidates()).hasSize(2);
        assertThat(out.candidates()).hasSize(1);
        assertThat(out.candidates().get(0).getGuideId()).isEqualTo(10L);
    }

    @Test
    void loadsFromDbWhenCandidatesEmptyAndRegionPresent() {
        GuideProfile p = GuideProfile.builder()
                .id(1L)
                .memberId(10L)
                .nickname("가이드A")
                .region("제주")
                .bio("감성 사진과 카페 중심 여행을 안내합니다.")
                .language("한국어, English")
                .pricePerHour(new BigDecimal("40000"))
                .isApproved(true)
                .isActive(true)
                .build();
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionEqualsIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(p)));
        when(guideFeedRepository.findByGuideProfile_IdInAndIsDeletedFalse(List.of(1L)))
                .thenReturn(List.of(
                        GuideFeed.builder()
                                .id(100L)
                                .guideProfile(p)
                                .content("감성 카페와 오션뷰 해변 포토스팟을 안내합니다.")
                                .build()
                ));
        when(guideCareerRepository.findByGuideProfile_IdIn(List.of(1L)))
                .thenReturn(List.of(
                        GuideCareer.builder()
                                .id(200L)
                                .guideProfile(p)
                                .title("현지 시장 로컬 투어")
                                .description("야경 맛집 코스 운영")
                                .acquiredAt(LocalDate.of(2024, 1, 1))
                                .build()
                ));
        when(refundRepository.countApprovedRefundsGroupedByGuideId(any(), eq(RefundStatus.APPROVED)))
                .thenReturn(List.of());
        when(matchRequestRepository.countAllGroupedByGuideId(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 4L}));
        when(matchRequestRepository.countByGuideIdAndStatusInGrouped(
                eq(List.of(1L)),
                eq(List.of(
                        MatchRequestStatus.ACCEPTED,
                        MatchRequestStatus.PAID,
                        MatchRequestStatus.IN_PROGRESS,
                        MatchRequestStatus.COMPLETED
                ))
        )).thenReturn(List.<Object[]>of(new Object[]{1L, 2L}));

        when(chatRoomRepository.countRoomsGroupedByParticipantUserId(List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 3L}));

        GuideCandidateBundle out =
                provider.getCandidates("제주에서 힐링하고 싶어요", 3, List.of(), null, null);

        assertThat(out.candidates()).hasSize(1);
        assertThat(out.unfilteredCandidates()).hasSize(1);
        assertThat(out.candidates().get(0).getGuideId()).isEqualTo(1L);
        assertThat(out.candidates().get(0).getRegion()).isEqualTo("제주");
        assertThat(out.candidates().get(0).getLanguages()).containsExactly("한국어", "English");
        assertThat(out.candidates().get(0).getPriceLevel()).isEqualTo("낮음");
        assertThat(out.candidates().get(0).getGuideStyle()).isEqualTo("감성");
        assertThat(out.candidates().get(0).getSpecialtyTags()).contains("카페", "바다", "사진", "시장", "야경", "맛집");
        assertThat(out.candidates().get(0).getApprovedRefundCount()).isZero();
        assertThat(out.candidates().get(0).getMatchRequestCount()).isEqualTo(4);
        assertThat(out.candidates().get(0).getProgressedMatchCount()).isEqualTo(2);
        assertThat(out.candidates().get(0).getChatStartCount()).isEqualTo(3);
    }

    @Test
    void excludesBookedPaidGuideFromDbPoolWhenDesiredDateSet() {
        LocalDate tourDate = LocalDate.of(2026, 4, 28);
        when(guideScheduleRepository.findGuideProfileIdsBookedAndPaidBetween(tourDate, tourDate, GuideScheduleStatus.BOOKED))
                .thenReturn(List.of(1L));

        GuideProfile p = GuideProfile.builder()
                .id(1L)
                .memberId(10L)
                .nickname("가이드A")
                .region("제주")
                .bio("감성 사진과 카페 중심 여행을 안내합니다.")
                .language("한국어, English")
                .pricePerHour(new BigDecimal("40000"))
                .isApproved(true)
                .isActive(true)
                .build();
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionEqualsIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(p)));

        GuideCandidateBundle out =
                provider.getCandidates("제주에서 힐링하고 싶어요", 3, List.of(), tourDate, tourDate);

        assertThat(out.unfilteredCandidates()).isNotNull();
        assertThat(out.candidates()).isEmpty();
        verify(guideScheduleRepository).findGuideProfileIdsBookedAndPaidBetween(tourDate, tourDate, GuideScheduleStatus.BOOKED);
    }

    @Test
    void fallsBackToGlobalPoolWhenRegionQueryEmpty() {
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionEqualsIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));
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
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrue(PageRequest.of(
                0,
                DbBackedGuideCandidateProvider.DEFAULT_MAX_FETCH_SIZE,
                Sort.by(Sort.Direction.DESC, "id")
        ))).thenReturn(new PageImpl<>(List.of(p)));
        when(guideFeedRepository.findByGuideProfile_IdInAndIsDeletedFalse(List.of(2L)))
                .thenReturn(List.of());
        when(guideCareerRepository.findByGuideProfile_IdIn(List.of(2L)))
                .thenReturn(List.of());
        when(refundRepository.countApprovedRefundsGroupedByGuideId(any(), eq(RefundStatus.APPROVED)))
                .thenReturn(List.of());
        when(matchRequestRepository.countAllGroupedByGuideId(List.of(2L)))
                .thenReturn(List.of());
        when(matchRequestRepository.countByGuideIdAndStatusInGrouped(any(), any()))
                .thenReturn(List.of());
        when(chatRoomRepository.countRoomsGroupedByParticipantUserId(List.of(11L)))
                .thenReturn(List.of());

        GuideCandidateBundle out =
                provider.getCandidates("제주 가고 싶어요", 3, null, null, null);

        assertThat(out.candidates()).hasSize(1);
        assertThat(out.candidates().get(0).getGuideId()).isEqualTo(2L);
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
        when(guideFeedRepository.findByGuideProfile_IdInAndIsDeletedFalse(List.of(3L)))
                .thenReturn(List.of());
        when(guideCareerRepository.findByGuideProfile_IdIn(List.of(3L)))
                .thenReturn(List.of());
        when(refundRepository.countApprovedRefundsGroupedByGuideId(any(), eq(RefundStatus.APPROVED)))
                .thenReturn(List.of());
        when(matchRequestRepository.countAllGroupedByGuideId(List.of(3L)))
                .thenReturn(List.of());
        when(matchRequestRepository.countByGuideIdAndStatusInGrouped(any(), any()))
                .thenReturn(List.of());
        when(chatRoomRepository.countRoomsGroupedByParticipantUserId(List.of(12L)))
                .thenReturn(List.of());

        GuideCandidateBundle out =
                provider.getCandidates("그냥 여행 가고 싶어요", 3, List.of(), null, null);

        assertThat(out.candidates()).hasSize(1);
        assertThat(out.candidates().get(0).getGuideId()).isEqualTo(3L);
        verify(guideProfileRepository, never()).findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(
                any(),
                any(Pageable.class)
        );
    }

    /**
     * 회귀: 기간(from~to)에 대해 스케줄 쿼리가 동일 경계로 호출되고, 클라이언트 후보는 필터만 제거·비필터는 유지(특별 제시 조립용).
     */
    @Test
    void regression_dateRange_clientPath_queriesScheduleAndKeepsUnfiltered() {
        LocalDate from = LocalDate.of(2026, 4, 28);
        LocalDate to = LocalDate.of(2026, 4, 30);
        when(guideScheduleRepository.findGuideProfileIdsBookedAndPaidBetween(from, to, GuideScheduleStatus.BOOKED))
                .thenReturn(List.of(9L));
        List<GuideRecommendRequest.GuideCandidateDto> client = List.of(
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(9L)
                        .guideName("바쁜가이드")
                        .region("제주")
                        .guideStyle("로컬")
                        .priceLevel("중간")
                        .specialtyTags(List.of("맛집"))
                        .languages(List.of("한국어"))
                        .build(),
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(10L)
                        .guideName("여유가이드")
                        .region("제주")
                        .guideStyle("로컬")
                        .priceLevel("중간")
                        .specialtyTags(List.of("맛집"))
                        .languages(List.of("한국어"))
                        .build()
        );
        GuideCandidateBundle out =
                provider.getCandidates("제주 맛집", 3, client, from, to);
        assertThat(out.unfilteredCandidates()).hasSize(2);
        assertThat(out.candidates()).hasSize(1);
        assertThat(out.candidates().get(0).getGuideId()).isEqualTo(10L);
        verify(guideScheduleRepository).findGuideProfileIdsBookedAndPaidBetween(from, to, GuideScheduleStatus.BOOKED);
    }

    /**
     * 회귀: API에서 desiredTourDateFrom &gt; desiredTourDateTo 인 경우,
     * {@link DbBackedGuideCandidateProvider}는 resolveBookedPaidGuideIds에서 from을 to 이하로 맞춰 단일 일자 구간으로 조회한다(현재 계약).
     */
    @Test
    void regression_reversedDateBounds_normalizeScheduleQueryRange() {
        LocalDate later = LocalDate.of(2026, 4, 30);
        LocalDate earlier = LocalDate.of(2026, 4, 28);
        when(guideScheduleRepository.findGuideProfileIdsBookedAndPaidBetween(earlier, earlier, GuideScheduleStatus.BOOKED))
                .thenReturn(List.of(9L));
        List<GuideRecommendRequest.GuideCandidateDto> client = List.of(
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(9L)
                        .guideName("A")
                        .region("제주")
                        .guideStyle("로컬")
                        .priceLevel("중간")
                        .specialtyTags(List.of("맛집"))
                        .languages(List.of("한국어"))
                        .build(),
                GuideRecommendRequest.GuideCandidateDto.builder()
                        .guideId(10L)
                        .guideName("B")
                        .region("제주")
                        .guideStyle("로컬")
                        .priceLevel("중간")
                        .specialtyTags(List.of("맛집"))
                        .languages(List.of("한국어"))
                        .build()
        );
        provider.getCandidates("제주", 3, client, later, earlier);
        verify(guideScheduleRepository).findGuideProfileIdsBookedAndPaidBetween(earlier, earlier, GuideScheduleStatus.BOOKED);
    }

    /**
     * 회귀: DB 풀에서 기간 중 BOOKED+PAID인 가이드만 필터에서 빠지고, 비필터 풀에는 남아 특별 제시 비교에 쓸 수 있다.
     */
    @Test
    void regression_dbPool_multiDayRange_excludesBookedFromFiltered_unfilteredRetainsBoth() {
        LocalDate from = LocalDate.of(2026, 4, 28);
        LocalDate to = LocalDate.of(2026, 4, 30);
        when(guideScheduleRepository.findGuideProfileIdsBookedAndPaidBetween(from, to, GuideScheduleStatus.BOOKED))
                .thenReturn(List.of(1L));

        GuideProfile p1 = GuideProfile.builder()
                .id(1L)
                .memberId(10L)
                .nickname("예약많음")
                .region("제주")
                .bio("카페 투어")
                .language("한국어")
                .pricePerHour(new BigDecimal("40000"))
                .isApproved(true)
                .isActive(true)
                .build();
        GuideProfile p2 = GuideProfile.builder()
                .id(2L)
                .memberId(11L)
                .nickname("여유")
                .region("제주")
                .bio("해변 산책")
                .language("한국어")
                .pricePerHour(new BigDecimal("50000"))
                .isApproved(true)
                .isActive(true)
                .build();
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionEqualsIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));
        when(guideProfileRepository.findByIsApprovedTrueAndIsActiveTrueAndRegionContainingIgnoreCase(
                eq("제주"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(p1, p2)));
        when(guideFeedRepository.findByGuideProfile_IdInAndIsDeletedFalse(argThat(ids -> idSetEquals(ids, 1L, 2L))))
                .thenReturn(List.of());
        when(guideCareerRepository.findByGuideProfile_IdIn(argThat(ids -> idSetEquals(ids, 1L, 2L))))
                .thenReturn(List.of());
        when(refundRepository.countApprovedRefundsGroupedByGuideId(any(), eq(RefundStatus.APPROVED)))
                .thenReturn(List.of());
        when(matchRequestRepository.countAllGroupedByGuideId(argThat(ids -> idSetEquals(ids, 1L, 2L))))
                .thenReturn(List.of());
        when(matchRequestRepository.countByGuideIdAndStatusInGrouped(
                argThat(ids -> idSetEquals(ids, 1L, 2L)),
                eq(List.of(
                        MatchRequestStatus.ACCEPTED,
                        MatchRequestStatus.PAID,
                        MatchRequestStatus.IN_PROGRESS,
                        MatchRequestStatus.COMPLETED
                ))
        )).thenReturn(List.of());
        when(chatRoomRepository.countRoomsGroupedByParticipantUserId(argThat(ids -> idSetEquals(ids, 10L, 11L))))
                .thenReturn(List.of());

        GuideCandidateBundle out =
                provider.getCandidates("제주에서 힐링", 3, List.of(), from, to);

        assertThat(out.unfilteredCandidates()).hasSize(2);
        assertThat(out.unfilteredCandidates()).extracting(GuideRecommendRequest.GuideCandidateDto::getGuideId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(out.candidates()).hasSize(1);
        assertThat(out.candidates().get(0).getGuideId()).isEqualTo(2L);
        verify(guideScheduleRepository).findGuideProfileIdsBookedAndPaidBetween(from, to, GuideScheduleStatus.BOOKED);
    }

    private static boolean idSetEquals(List<Long> ids, Long a, Long b) {
        if (ids == null) {
            return false;
        }
        return new HashSet<>(ids).equals(Set.of(a, b));
    }
}
