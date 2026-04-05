package com.team6.domain.guide.entity;

import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 가이드 프로필 엔티티 (guide_profiles 테이블)
 */
@Entity
@Table(name = "guide_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class GuideProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_id")
    private Long guideId; // 가이드 프로필 고유 ID (PK)

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId; // 회원 ID (JWT에서 추출, 엔티티 직접 참조 금지)

    @Column(nullable = false, length = 50)
    private String nickname; // 가이드 닉네임

    @Column(name = "profile_image", length = 500)
    private String profileImage; // 프로필 이미지 URL

    @Column(columnDefinition = "TEXT")
    private String bio; // 자기소개

    @Column(nullable = false, length = 100)
    private String region; // 활동 지역

    @Column(length = 50)
    private String language; // 구사 가능 언어

    @Column(name = "price_per_hour", precision = 10, scale = 2)
    private BigDecimal pricePerHour; // 시간당 가격

    @Column(name = "average_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO; // 평균 평점

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0; // 리뷰 수

    @Column(name = "is_approved", nullable = false)
    @Builder.Default
    private Boolean isApproved = false; // 관리자 승인 여부

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true; // 활성화 여부

    // 수정 가능한 필드 업데이트
    public void update(String nickname, String profileImage, String bio,
                       String region, String language, BigDecimal pricePerHour, Boolean isActive) {
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.bio = bio;
        this.region = region;
        this.language = language;
        this.pricePerHour = pricePerHour;
        this.isActive = isActive;
    }
}
