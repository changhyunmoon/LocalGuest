package com.team6.domain.member.dto.response;

import com.team6.domain.member.entity.*;
import lombok.*;

import java.util.Set;

@Getter
@AllArgsConstructor
@Builder
public class TravelPreferenceResponse {

    private Long id;
    private Set<TravelConcept> concepts;
    private PlanningStyle planningStyle;
    private CompanionType companionType;
    private Integer preferredDurationDays;
    private DistancePreference distancePreference;
    private GuideMatchingStyle guideMatchingStyle;
    private Set<InterestRegion> interestRegions;

    public static TravelPreferenceResponse from(TravelPreference preference) {
        if (preference == null) {
            return null;
        }

        return TravelPreferenceResponse.builder()
                .id(preference.getId())
                .concepts(preference.getConcepts())
                .planningStyle(preference.getPlanningStyle())
                .companionType(preference.getCompanionType())
                .preferredDurationDays(preference.getPreferredDurationDays())
                .distancePreference(preference.getDistancePreference())
                .guideMatchingStyle(preference.getGuideMatchingStyle())
                .interestRegions(preference.getInterestRegions())
                .build();
    }
}