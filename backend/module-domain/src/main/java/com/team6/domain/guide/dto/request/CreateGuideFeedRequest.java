package com.team6.domain.guide.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 가이드 피드 등록 요청 DTO (F06-03)
 */
@Getter
@NoArgsConstructor
public class CreateGuideFeedRequest {

    @NotBlank
    private String content;       // 피드 본문 내용 (필수)

    private String imageUrl;      // 단일 이미지 URL (하위 호환용)

    private List<String> imageUrls; // 다중 이미지 URL 목록 (최대 10장)
}
