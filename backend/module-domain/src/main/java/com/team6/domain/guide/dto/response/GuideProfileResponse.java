package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideProfile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 가이드 프로필 응답 DTO
 */
@Getter
@Builder
public class GuideProfileResponse {

    private Long guideId;               // 가이드 프로필 ID
    private Long memberId;              // 회원 ID
    private String nickname;            // 가이드 닉네임
    private String profileImage;        // 프로필 이미지 URL
    private String bio;                 // 자기소개
    private String region;              // 활동 지역
    private String language;            // 구사 가능 언어
    private BigDecimal pricePerHour;    // 시간당 가격
    private BigDecimal averageRating;   // 평균 평점
    private Integer reviewCount;        // 리뷰 수
    private Boolean isApproved;         // 관리자 승인 여부
    private Boolean isActive;           // 활성화 여부
    private Integer residenceYears;     // 해당 지역 거주 연수
    private String localStory;          // 지역 스토리
    private String keywords;            // AI 매칭용 키워드 (F06-02)
    private String defaultCourse;       // 디폴트 코스 (F06-02)
    private String guideStyle;          // 가이드 스타일 (F06-02)
    private LocalDateTime createdAt;    // 등록일시
    private LocalDateTime updatedAt;    // 수정일시

    // Entity → DTO 변환
    public static GuideProfileResponse from(GuideProfile profile) {
        return GuideProfileResponse.builder()
                .guideId(profile.getId())
                .memberId(profile.getMemberId())
                .nickname(profile.getNickname())
                .profileImage(profile.getProfileImage())
                .bio(profile.getBio())
                .region(profile.getRegion())
                .language(profile.getLanguage())
                .pricePerHour(profile.getPricePerHour())
                .averageRating(profile.getAverageRating())
                .reviewCount(profile.getReviewCount())
                .isApproved(profile.getIsApproved())
                .isActive(profile.getIsActive())
                .residenceYears(profile.getResidenceYears())
                .localStory(profile.getLocalStory())
                .keywords(profile.getKeywords())
                .defaultCourse(profile.getDefaultCourse())
                .guideStyle(profile.getGuideStyle())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
