package com.team6.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "guest_profile")
public class GuestProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "member_id", unique = true, nullable = false)
    private Member member;

    // Guest 프로필 이미지
    @Column(name = "profile_image_url")
    private String profileImageUrl;

    // 간단한 자기소개
    @Column(length = 500)
    private String bio;

    // 여행 선호도
    @OneToOne(mappedBy = "guestProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private TravelPreference travelPreference;

    void setMember(Member member) {
        this.member = member;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updateBio(String bio) {
        this.bio = bio;
    }

    public void setTravelPreference(TravelPreference preference) {
        this.travelPreference = preference;
        if (preference != null) {
            preference.setGuestProfile(this);
        }
    }
}