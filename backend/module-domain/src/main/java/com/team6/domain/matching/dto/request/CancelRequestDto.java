package com.team6.domain.matching.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelRequestDto {

    private Long requestId;     // 매칭 요청 ID

    @NotBlank(message = "취소 사유는 필수입니다")
    private String cancelReason; // 취소 사유
}