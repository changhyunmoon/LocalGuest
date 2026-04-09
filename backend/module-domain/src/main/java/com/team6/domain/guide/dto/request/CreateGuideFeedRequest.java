package com.team6.domain.guide.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가이드 피드 등록 요청 DTO (F06-03)
 */
@Getter
@NoArgsConstructor
public class CreateGuideFeedRequest {

    @NotBlank
    private String content;  // 피드 본문 내용 (필수)

    private String imageUrl; // 피드 이미지 URL (선택)
}
