package com.team6.domain.matching.service;

import com.team6.domain.matching.dto.request.RefundRequestDto;
import com.team6.domain.matching.dto.response.RefundResponseDto;
import com.team6.domain.matching.entity.Payment;
import com.team6.domain.matching.entity.Refund;
import com.team6.domain.matching.entity.enums.PaymentStatus;
import com.team6.domain.matching.entity.enums.RefundType;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.PaymentRepository;
import com.team6.domain.matching.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    // F05-04: 가이드 불만족 환불 신청 및 처리 (결제 후 2시간 이내)
    public RefundResponseDto requestGuestRefund(Long guestId, RefundRequestDto request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getPayerId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_NOT_COMPLETED);
        }

        if (payment.getRefundDeadline() == null || LocalDateTime.now().isAfter(payment.getRefundDeadline())) {
            throw new MatchingException(MatchingErrorCode.REFUND_DEADLINE_EXCEEDED);
        }

        if (refundRepository.findByPayment_Id(payment.getId()).isPresent()) {
            throw new MatchingException(MatchingErrorCode.REFUND_ALREADY_REQUESTED);
        }

        Refund refund = Refund.builder()
                .payment(payment)
                .requesterId(guestId)
                .refundType(RefundType.MANUAL)
                .reason(request.getReason())
                .evidenceUrl(request.getEvidenceUrl())
                .aiProcessed(true)
                .build();

        // 현재 F05-04 요구사항에 맞춰 신청과 동시에 처리 완료로 반영
        refund.approve();
        payment.refund();

        Refund saved = refundRepository.save(refund);
        log.info("[F05-04] 가이드 불만족 환불 처리 완료 — paymentId={}, guestId={}, refundId={}",
                payment.getId(), guestId, saved.getId());
        return RefundResponseDto.from(saved);
    }
}
