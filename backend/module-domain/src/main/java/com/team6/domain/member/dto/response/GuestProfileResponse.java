package com.team6.domain.member.dto.response;

import com.team6.domain.member.dto.response.TravelPreferenceResponse;
import com.team6.domain.member.entity.GuestProfile;
import lombok.*;

@Getter
@AllArgsConstructor
@Builder
public class GuestProfileResponse {

    private Long id;
    private String profileImageUrl;
    private String bio;
    private TravelPreferenceResponse travelPreference;

    public static GuestProfileResponse from(GuestProfile profile) {
        if (profile == null) {
            return null;
        }

        return GuestProfileResponse.builder()
                .id(profile.getId())
                .profileImageUrl(profile.getProfileImageUrl())
                .bio(profile.getBio())
                .travelPreference(TravelPreferenceResponse.from(profile.getTravelPreference()))
                .build();
    }
}