package com.team6.domain.member.dto.request;

import com.team6.domain.member.entity.*;
import lombok.*;

import java.util.Set;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelPreferenceRequest {

    private Set<TravelConcept> concepts;
    private PlanningStyle planningStyle;
    private CompanionType companionType;
    private Integer preferredDurationDays;
    private DistancePreference distancePreference;
    private GuideMatchingStyle guideMatchingStyle;
    private Set<InterestRegion> interestRegions;

    public TravelPreference toEntity(GuestProfile guestProfile) {
        return TravelPreference.builder()
                .guestProfile(guestProfile)
                .concepts(concepts)
                .planningStyle(planningStyle)
                .companionType(companionType)
                .preferredDurationDays(preferredDurationDays)
                .distancePreference(distancePreference)
                .guideMatchingStyle(guideMatchingStyle)
                .interestRegions(interestRegions)
                .build();
    }
}