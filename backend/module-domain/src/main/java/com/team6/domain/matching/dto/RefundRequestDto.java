package com.team6.domain.matching.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefundRequestDto {

    private Long paymentId;      // 결제 ID
    private String reason;       // 환불 사유
    private String evidenceUrl;  // 증빙 자료 URL (선택)
}