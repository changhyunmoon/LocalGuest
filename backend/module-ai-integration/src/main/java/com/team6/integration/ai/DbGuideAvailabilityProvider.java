package com.team6.integration.ai;

import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.domain.guide.repository.GuideScheduleRepository;
import com.team6.module.ai.support.GuideAvailabilityProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Primary
@RequiredArgsConstructor
public class DbGuideAvailabilityProvider implements GuideAvailabilityProvider {

    private final GuideScheduleRepository guideScheduleRepository;

    @Override
    public List<LocalDate> availableDates(Long guideId, LocalDate from, LocalDate to) {
        if (guideId == null || from == null) {
            return List.of();
        }
        LocalDate end = (to == null) ? from : to;
        LocalDate start = from.isAfter(end) ? end : from;
        LocalDate finish = from.isAfter(end) ? from : end;

        List<LocalDate> booked = guideScheduleRepository.findBookedPaidDatesByGuideIdBetween(
                guideId,
                start,
                finish,
                GuideScheduleStatus.BOOKED
        );
        Set<LocalDate> bookedSet = new HashSet<>();
        if (booked != null) {
            for (LocalDate d : booked) {
                if (d != null) bookedSet.add(d);
            }
        }

        int span = (int) (finish.toEpochDay() - start.toEpochDay());
        if (span < 0) {
            return List.of();
        }
        // 과도한 기간은 보호 (UX도 불명확). 0~30일까지만 안내한다.
        if (span > 30) {
            finish = start.plusDays(30);
        }

        java.util.ArrayList<LocalDate> out = new java.util.ArrayList<>();
        LocalDate cur = start;
        while (!cur.isAfter(finish)) {
            if (!bookedSet.contains(cur)) {
                out.add(cur);
            }
            cur = cur.plusDays(1);
        }
        return out;
    }
}

