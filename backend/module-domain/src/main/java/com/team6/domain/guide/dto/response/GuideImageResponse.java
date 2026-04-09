package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideImage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 가이드 이미지 응답 DTO
 */
@Getter
@Builder
public class GuideImageResponse {

    private Long imageId;            // 이미지 ID
    private Long guideId;            // 가이드 프로필 ID
    private String imageUrl;         // 이미지 URL
    private Integer sortOrder;       // 정렬 순서
    private Boolean isMain;          // 대표 이미지 여부
    private LocalDateTime createdAt; // 등록일시

    // Entity → DTO 변환
    public static GuideImageResponse from(GuideImage image) {
        return GuideImageResponse.builder()
                .imageId(image.getId())
                .guideId(image.getGuideProfile().getId()) // 연관 엔티티에서 ID 추출
                .imageUrl(image.getImageUrl())
                .sortOrder(image.getSortOrder())
                .isMain(image.getIsMain())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
