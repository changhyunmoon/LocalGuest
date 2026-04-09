package com.team6.domain.guide.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 가이드 경력 수정 요청 DTO
 */
@Getter
@NoArgsConstructor
public class UpdateGuideCareerRequest {

    @NotBlank
    private String title;           // 자격/경력명

    private String description;     // 자격/경력 설명 (선택)

    @NotNull
    private LocalDate acquiredAt;   // 자격 취득일
}
