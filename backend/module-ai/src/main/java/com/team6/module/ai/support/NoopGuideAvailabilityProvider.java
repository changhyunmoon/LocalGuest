package com.team6.module.ai.support;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class NoopGuideAvailabilityProvider implements GuideAvailabilityProvider {
    @Override
    public List<LocalDate> availableDates(Long guideId, LocalDate from, LocalDate to) {
        return List.of();
    }
}

