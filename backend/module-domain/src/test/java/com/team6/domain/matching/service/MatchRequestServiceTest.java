package com.team6.domain.matching.service;

import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.client.GuideScheduleSyncClient;
import com.team6.domain.matching.dto.request.MatchRequestCreateRequest;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchRequestServiceTest {

    @Mock
    private MatchRequestRepository matchRequestRepository;

    @Mock
    private GuideScheduleSyncClient guideScheduleSyncClient;

    @Mock
    private GuideProfileRepository guideProfileRepository;

    @InjectMocks
    private MatchRequestService matchRequestService;

    @Test
    void 매칭요청_생성시_본인_가이드프로필이면_예외() {
        Long guestId = 100L;
        Long guideProfileId = 20L;
        MatchRequestCreateRequest request = createRequest(guideProfileId, 300L);

        GuideProfile profile = GuideProfile.builder()
                .id(guideProfileId)
                .memberId(guestId)
                .nickname("self-guide")
                .region("Seoul")
                .build();
        when(guideProfileRepository.findById(guideProfileId)).thenReturn(Optional.of(profile));

        MatchingException ex = assertThrows(MatchingException.class,
                () -> matchRequestService.createMatchRequest(guestId, request));

        assertEquals(MatchingErrorCode.GUEST_GUIDE_SAME, ex.getErrorCode());
        verify(matchRequestRepository, never()).save(any());
        verify(guideScheduleSyncClient, never()).markPending(any(), any(), any(), any());
    }

    @Test
    void 매칭요청_생성시_다른가이드면_markPending_정상호출() {
        Long guestId = 100L;
        Long guideProfileId = 20L;
        Long guideMemberId = 999L;
        MatchRequestCreateRequest request = createRequest(guideProfileId, 300L);

        GuideProfile profile = GuideProfile.builder()
                .id(guideProfileId)
                .memberId(guideMemberId)
                .nickname("other-guide")
                .region("Busan")
                .build();
        when(guideProfileRepository.findById(guideProfileId)).thenReturn(Optional.of(profile));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(invocation -> {
            MatchRequest saved = invocation.getArgument(0);
            setField(saved, "id", 1L);
            setField(saved, "status", MatchRequestStatus.PENDING);
            return saved;
        });

        matchRequestService.createMatchRequest(guestId, request);

        verify(guideScheduleSyncClient).markPending(guideProfileId, 300L, 1L, guideMemberId);
    }

    private MatchRequestCreateRequest createRequest(Long guideId, Long scheduleId) {
        MatchRequestCreateRequest request = new MatchRequestCreateRequest();
        setField(request, "guideId", guideId);
        setField(request, "guideScheduleId", scheduleId);
        setField(request, "destination", "Seoul");
        setField(request, "concept", "food");
        setField(request, "desiredDate", LocalDate.of(2026, 4, 20));
        setField(request, "desiredBudget", 50000);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

