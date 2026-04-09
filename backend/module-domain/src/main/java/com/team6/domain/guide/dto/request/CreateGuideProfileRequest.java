package com.team6.domain.guide.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 가이드 프로필 등록 요청 DTO (F06-01)
 */
@Getter
@NoArgsConstructor
public class CreateGuideProfileRequest {

    @NotBlank
    private String nickname;        // 가이드 닉네임

    private String profileImage;    // 프로필 이미지 URL (선택)

    private String bio;             // 자기소개 (선택)

    @NotBlank
    private String region;          // 활동 지역

    @NotBlank
    private String language;        // 구사 가능 언어

    @NotNull
    private BigDecimal pricePerHour; // 시간당 가격

    private Integer residenceYears; // 해당 지역 거주 연수 (선택)

    private String localStory;      // 지역 스토리 (선택)
}
