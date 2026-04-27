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
    private String pgOrderNo;            // PG 주문번호 (승인 요청 시 사용)
    private String pgTransactionId;      // PG 거래 ID (완료 후)
    private String redirectUrl;          // 카카오 결제창 URL (ready 응답)
    private LocalDateTime paidAt;        // 결제 완료일시
    private LocalDateTime refundDeadline; // 환불 마감일시

    // Entity → DTO 변환
    public static PaymentResponseDto from(Payment payment) {
        return PaymentResponseDto.builder()
                .paymentId(payment.getId())
                .requestId(payment.getMatchRequest().getId())
                .amount(payment.getAmount())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .pgOrderNo(payment.getPgOrderNo())
                .pgTransactionId(payment.getPgTransactionId())
                .paidAt(payment.getPaidAt())
                .refundDeadline(payment.getRefundDeadline())
                .build();
    }

    public static PaymentResponseDto from(Payment payment, String redirectUrl) {
        return PaymentResponseDto.builder()
                .paymentId(payment.getId())
                .requestId(payment.getMatchRequest().getId())
                .amount(payment.getAmount())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .pgOrderNo(payment.getPgOrderNo())
                .pgTransactionId(payment.getPgTransactionId())
                .redirectUrl(redirectUrl)
                .paidAt(payment.getPaidAt())
                .refundDeadline(payment.getRefundDeadline())
                .build();
    }
}