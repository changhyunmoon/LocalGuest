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
    private Long id; // 가이드 프로필 고유 ID (PK)

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId; // 회원 ID (JWT에서 추출, 엔티티 직접 참조 금지)

    @Column(nullable = false, length = 50)
    private String nickname; // 가이드 닉네임

    @Column(name = "profile_image", length = 500)
    private String profileImage; // 프로필 이미지 URL

    @Column(columnDefinition = "TEXT")
    private String bio; // 자기소개

    @Column(nullable = false, length = 100)
    private String region; // 활동 지역

    @Column(length = 100)
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

    @Column(name = "residence_years")
    private Integer residenceYears; // 거주 연수

    @Column(name = "local_story", columnDefinition = "TEXT")
    private String localStory; // 로컬 스토리 소개

    @Column(columnDefinition = "TEXT")
    private String keywords; // AI 매칭용 키워드 (F06-02)

    @Column(name = "default_course", columnDefinition = "TEXT")
    private String defaultCourse; // 디폴트 코스 (F06-02)

    @Column(name = "guide_style", length = 100)
    private String guideStyle; // 가이드 스타일 (F06-02)

    // 수정 가능한 필드 업데이트
    public void update(String nickname, String profileImage, String bio,
                       String region, String language, BigDecimal pricePerHour,
                       Boolean isActive, Integer residenceYears, String localStory,
                       String keywords, String defaultCourse, String guideStyle) {
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.bio = bio;
        this.region = region;
        this.language = language;
        this.pricePerHour = pricePerHour;
        this.isActive = isActive;
        this.residenceYears = residenceYears;
        this.localStory = localStory;
        this.keywords = keywords;
        this.defaultCourse = defaultCourse;
        this.guideStyle = guideStyle;
    }
    // 활성화 상태 토글
    public void toggleActive() {
        this.isActive = !this.isActive;
    }

    // 관리자 승인
    public void approve() {
        this.isApproved = true;
    }

    // 평균 평점 및 리뷰 수 갱신 — review 도메인 연동 시 호출 (F06-07)
    public void updateRating(BigDecimal averageRating, Integer reviewCount) {
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }
}
