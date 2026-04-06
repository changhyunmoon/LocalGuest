package com.team6.domain.matching.dto.response;

import com.team6.domain.matching.entity.Refund;
import com.team6.domain.matching.entity.enums.RefundStatus;
import com.team6.domain.matching.entity.enums.RefundType;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class RefundResponseDto {

    private Long refundId;           // 환불 ID
    private Long paymentId;          // 결제 ID
    private RefundType refundType;   // 환불 유형
    private RefundStatus status;     // 환불 상태
    private LocalDateTime processedAt; // 처리 완료일시

    // Entity → DTO 변환
    public static RefundResponseDto from(Refund refund) {
        return RefundResponseDto.builder()
                .refundId(refund.getRefundId())
                .paymentId(refund.getPayment().getPaymentId())
                .refundType(refund.getRefundType())
                .status(refund.getStatus())
                .processedAt(refund.getProcessedAt())
                .build();
    }
}