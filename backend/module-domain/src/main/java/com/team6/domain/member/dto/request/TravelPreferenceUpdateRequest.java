package com.team6.domain.member.dto.request;

import com.team6.domain.member.entity.*;
import lombok.*;

import java.util.Set;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TravelPreferenceUpdateRequest {

    private Set<TravelConcept> concepts;
    private PlanningStyle planningStyle;
    private CompanionType companionType;
    private Integer preferredDurationDays;
    private DistancePreference distancePreference;
    private GuideMatchingStyle guideMatchingStyle;
    private Set<InterestRegion> interestRegions;
}