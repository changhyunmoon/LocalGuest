package com.team6.domain.matching.dto.response;

import com.team6.domain.matching.entity.Payment;
import com.team6.domain.matching.entity.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponseDto {

    private Long paymentId;              // 결제 ID
    private Long requestId;              // 매칭 요청 ID
    private Integer amount;              // 결제 금액
    private String paymentType;          // CHAT/ACCOMPANY/EXTENSION
    private PaymentStatus status;        // 결제 상태
    private LocalDateTime paidAt;        // 결제 완료일시
    private LocalDateTime refundDeadline; // 환불 마감일시

    // Entity → DTO 변환
    public static PaymentResponseDto from(Payment payment) {
        return PaymentResponseDto.builder()
                .paymentId(payment.getPaymentId())
                .requestId(payment.getMatchRequest().getRequestId())
                .amount(payment.getAmount())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .paidAt(payment.getPaidAt())
                .refundDeadline(payment.getRefundDeadline())
                .build();
    }
}