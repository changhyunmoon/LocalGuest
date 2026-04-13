package com.team6.domain.matching.service;

import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.TourExtension;
import com.team6.domain.matching.entity.enums.TourExtensionStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.repository.TourExtensionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourExtensionServiceTest {

    @Mock
    private TourExtensionRepository tourExtensionRepository;
    @Mock
    private MatchRequestRepository matchRequestRepository;
    @InjectMocks
    private TourExtensionService tourExtensionService;

    @Test
    void 연장조회_게스트본인_성공() {
        TourExtension extension = sampleExtension(10L, 20L);
        when(tourExtensionRepository.findByMatchRequest_Id(1L)).thenReturn(Optional.of(extension));

        assertEquals(1L, tourExtensionService.getByRequestId(1L, 10L).getRequestId());
    }

    @Test
    void 연장조회_가이드본인_성공() {
        TourExtension extension = sampleExtension(10L, 20L);
        when(tourExtensionRepository.findByMatchRequest_Id(1L)).thenReturn(Optional.of(extension));

        assertEquals(1L, tourExtensionService.getByRequestId(1L, 20L).getRequestId());
    }

    @Test
    void 연장조회_당사자아니면_권한예외() {
        TourExtension extension = sampleExtension(10L, 20L);
        when(tourExtensionRepository.findByMatchRequest_Id(1L)).thenReturn(Optional.of(extension));

        MatchingException ex = assertThrows(MatchingException.class,
                () -> tourExtensionService.getByRequestId(1L, 999L));
        assertEquals(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED, ex.getErrorCode());
    }

    private TourExtension sampleExtension(Long guestId, Long guideId) {
        MatchRequest matchRequest = MatchRequest.builder()
                .id(1L)
                .guestId(guestId)
                .guideId(guideId)
                .destination("Seoul")
                .desiredDate(LocalDate.now())
                .build();

        return TourExtension.builder()
                .id(100L)
                .matchRequest(matchRequest)
                .guestId(guestId)
                .extendedDate(LocalDate.now().plusDays(1))
                .extendedPrice(15000)
                .status(TourExtensionStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .deadlineAt(LocalDateTime.now().plusHours(1))
                .build();
    }
}
