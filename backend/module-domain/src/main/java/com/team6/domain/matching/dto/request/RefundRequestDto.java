package com.team6.domain.matching.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
public class RefundRequestDto {

    @NotNull
    private Long paymentId;      // 결제 ID

    @NotBlank
    private String reason;       // 환불 사유

    private String evidenceUrl;  // 증빙 자료 URL (선택)
}