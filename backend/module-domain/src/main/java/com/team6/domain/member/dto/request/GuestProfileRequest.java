package com.team6.domain.member.dto.request;

import com.team6.domain.member.entity.GuestProfile;
import com.team6.domain.member.entity.Member;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestProfileRequest {

    private String profileImageUrl;
    private String bio;
    private TravelPreferenceRequest travelPreference;

    public GuestProfile toEntity(Member member) {
        GuestProfile profile = GuestProfile.builder()
                .member(member)
                .profileImageUrl(profileImageUrl)
                .bio(bio)
                .build();

        // 여행 선호도가 있으면 설정
        if (travelPreference != null) {
            profile.setTravelPreference(travelPreference.toEntity(profile));
        }

        return profile;
    }
}