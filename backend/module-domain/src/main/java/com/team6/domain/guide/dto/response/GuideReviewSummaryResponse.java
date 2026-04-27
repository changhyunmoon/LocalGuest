package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideProfile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 가이드 리뷰 요약 응답 DTO (F06-07)
 * 리뷰 상세 목록은 review 도메인 GET /reviews/guide/{guideId} 직접 호출
 */
@Getter
@Builder
public class GuideReviewSummaryResponse {

    private Long guideId;            // 가이드 프로필 ID
    private BigDecimal averageRating; // 평균 평점
    private Integer reviewCount;      // 리뷰 수

    // Entity → DTO 변환
    public static GuideReviewSummaryResponse from(GuideProfile profile) {
        return GuideReviewSummaryResponse.builder()
                .guideId(profile.getId())
                .averageRating(profile.getAverageRating())
                .reviewCount(profile.getReviewCount())
                .build();
    }
}
