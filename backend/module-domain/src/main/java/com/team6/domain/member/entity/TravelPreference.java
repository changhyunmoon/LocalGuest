package com.team6.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "travel_preference")
public class TravelPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "guest_profile_id", unique = true, nullable = false)
    private GuestProfile guestProfile;

    // 선호 여행 컨셉 (복수 선택)
    @ElementCollection
    @CollectionTable(name = "travel_preference_concepts",
            joinColumns = @JoinColumn(name = "travel_preference_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "concept")
    @Builder.Default
    private Set<TravelConcept> concepts = new HashSet<>();

    // 여행 계획 스타일
    @Enumerated(EnumType.STRING)
    @Column(name = "planning_style", length = 50)
    private PlanningStyle planningStyle;

    // 주로 함께하는 여행
    @Enumerated(EnumType.STRING)
    @Column(name = "companion_type", length = 50)
    private CompanionType companionType;

    // 선호 여행 기간 (일 단위: 1=당일치기, 2=1박2일, 3=2박3일+)
    @Column(name = "preferred_duration_days")
    private Integer preferredDurationDays;

    // 선호 이동 거리
    @Enumerated(EnumType.STRING)
    @Column(name = "distance_preference", length = 50)
    private DistancePreference distancePreference;

    // 가이드 매칭 방식
    @Enumerated(EnumType.STRING)
    @Column(name = "guide_matching_style", length = 50)
    private GuideMatchingStyle guideMatchingStyle;

    // 관심 지역 (복수 선택)
    @ElementCollection
    @CollectionTable(name = "travel_preference_regions",
            joinColumns = @JoinColumn(name = "travel_preference_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "region")
    @Builder.Default
    private Set<InterestRegion> interestRegions = new HashSet<>();

    void setGuestProfile(GuestProfile guestProfile) {
        this.guestProfile = guestProfile;
    }

    public void update(
            Set<TravelConcept> concepts,
            PlanningStyle planningStyle,
            CompanionType companionType,
            Integer preferredDurationDays,
            DistancePreference distancePreference,
            GuideMatchingStyle guideMatchingStyle,
            Set<InterestRegion> interestRegions
    ) {
        if (concepts != null) {
            this.concepts = new HashSet<>(concepts);
        }
        if (planningStyle != null) {
            this.planningStyle = planningStyle;
        }
        if (companionType != null) {
            this.companionType = companionType;
        }
        if (preferredDurationDays != null) {
            this.preferredDurationDays = preferredDurationDays;
        }
        if (distancePreference != null) {
            this.distancePreference = distancePreference;
        }
        if (guideMatchingStyle != null) {
            this.guideMatchingStyle = guideMatchingStyle;
        }
        if (interestRegions != null) {
            this.interestRegions = new HashSet<>(interestRegions);
        }
    }
}