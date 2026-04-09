package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideCareer;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 가이드 경력 응답 DTO
 */
@Getter
@Builder
public class GuideCareerResponse {

    private Long careerId;          // 경력 ID
    private Long guideId;           // 가이드 프로필 ID
    private String title;           // 자격/경력명
    private String description;     // 자격/경력 설명
    private LocalDate acquiredAt;   // 자격 취득일
    private LocalDateTime createdAt; // 등록일시

    // Entity → DTO 변환
    public static GuideCareerResponse from(GuideCareer career) {
        return GuideCareerResponse.builder()
                .careerId(career.getId())
                .guideId(career.getGuideProfile().getId()) // 연관 엔티티에서 ID 추출
                .title(career.getTitle())
                .description(career.getDescription())
                .acquiredAt(career.getAcquiredAt())
                .createdAt(career.getCreatedAt())
                .build();
    }
}
