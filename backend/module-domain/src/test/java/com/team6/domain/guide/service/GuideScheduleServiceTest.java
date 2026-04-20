package com.team6.domain.guide.service;

import com.team6.domain.guide.dto.response.GuideScheduleResponse;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.entity.GuideSchedule;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.guide.repository.GuideScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuideScheduleServiceTest {

    @Mock
    private GuideScheduleRepository guideScheduleRepository;

    @Mock
    private GuideProfileRepository guideProfileRepository;

    @InjectMocks
    private GuideScheduleService guideScheduleService;

    @Test
    void markAsPaid_결제확정시_PENDING이면_BOOKED로_전환후_isPaid_true() {
        Long guideId = 10L;
        Long scheduleId = 100L;
        GuideSchedule schedule = createSchedule(scheduleId, guideId, GuideScheduleStatus.PENDING, false);
        when(guideScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        GuideScheduleResponse response = guideScheduleService.markAsPaid(scheduleId, guideId, null);

        assertEquals(GuideScheduleStatus.BOOKED, response.getStatus());
        assertEquals(true, response.getIsPaid());
    }

    @Test
    void markAsPaid_결제확정시_BOOKED이면_isPaid만_true_유지() {
        Long guideId = 10L;
        Long scheduleId = 101L;
        GuideSchedule schedule = createSchedule(scheduleId, guideId, GuideScheduleStatus.BOOKED, false);
        when(guideScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        GuideScheduleResponse response = guideScheduleService.markAsPaid(scheduleId, guideId, null);

        assertEquals(GuideScheduleStatus.BOOKED, response.getStatus());
        assertEquals(true, response.getIsPaid());
    }

    @Test
    void markAsPaid_결제확정시_PENDING_BOOKED_아닌경우_예외() {
        Long guideId = 10L;
        Long scheduleId = 102L;
        GuideSchedule schedule = createSchedule(scheduleId, guideId, GuideScheduleStatus.AVAILABLE, false);
        when(guideScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        GuideException ex = assertThrows(GuideException.class,
                () -> guideScheduleService.markAsPaid(scheduleId, guideId, null));

        assertEquals(GuideErrorCode.SCHEDULE_NOT_BOOKED, ex.getErrorCode());
    }

    private GuideSchedule createSchedule(Long scheduleId, Long guideId, GuideScheduleStatus status, boolean isPaid) {
        GuideProfile profile = GuideProfile.builder()
                .id(guideId)
                .memberId(999L)
                .build();

        return GuideSchedule.builder()
                .id(scheduleId)
                .guideProfile(profile)
                .availableDate(LocalDate.of(2026, 4, 20))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .status(status)
                .isPaid(isPaid)
                .build();
    }
}

