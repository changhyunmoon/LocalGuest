package com.team6.domain.guide.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가이드 피드 이미지 등록 요청 DTO (F06-03)
 */
@Getter
@NoArgsConstructor
public class CreateGuideImageRequest {

    @NotBlank
    private String imageUrl;    // 이미지 저장 URL

    @NotNull
    private Integer sortOrder;  // 이미지 정렬 순서

    private Boolean isMain = false; // 대표 이미지 여부 (선택, 기본값 false)
}
