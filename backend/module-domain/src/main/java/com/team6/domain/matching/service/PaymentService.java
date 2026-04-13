package com.team6.domain.matching.service;

import com.team6.domain.matching.client.FakePgClient;
import com.team6.domain.matching.dto.request.PaymentConfirmRequest;
import com.team6.domain.matching.dto.request.PaymentCreateRequest;
import com.team6.domain.matching.dto.request.RefundRequestDto;
import com.team6.domain.matching.dto.response.PaymentResponseDto;
import com.team6.domain.matching.dto.response.RefundResponseDto;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.Payment;
import com.team6.domain.matching.entity.Refund;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.entity.enums.PaymentStatus;
import com.team6.domain.matching.entity.enums.RefundType;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.repository.PaymentRepository;
import com.team6.domain.matching.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final FakePgClient fakePgClient;

    /**
     * 결제 요청 생성 — PENDING, Stub PG 주문번호 발급.
     * 매칭 요청이 게스트 소유이며 ACCEPTED 일 때만 가능. (match_request_id, payment_type) 중복 불가.
     */
    public PaymentResponseDto createPayment(Long guestId, PaymentCreateRequest request) {
        MatchRequest matchRequest = matchRequestRepository.findById(request.getMatchRequestId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        if (matchRequest.getStatus() != MatchRequestStatus.ACCEPTED) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }

        if (paymentRepository.findByMatchRequest_IdAndPaymentType(matchRequest.getId(), request.getPaymentType())
                .isPresent()) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_DUPLICATE_TYPE);
        }

        String pgOrderNo = FakePgClient.STUB_PG_ORDER_PREFIX + UUID.randomUUID();

        Payment payment = Payment.builder()
                .matchRequest(matchRequest)
                .payerId(guestId)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .pgOrderNo(pgOrderNo)
                .status(PaymentStatus.PENDING)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("[Payment] 결제 요청 생성 — paymentId={}, requestId={}, guestId={}, type={}, pgOrderNo={}",
                saved.getId(), matchRequest.getId(), guestId, request.getPaymentType(), pgOrderNo);
        return PaymentResponseDto.from(saved);
    }

    /**
     * Stub PG 승인: 주문번호·금액 검증 후 COMPLETED, paidAt/refundDeadline 설정, 매칭 요청 ACCEPTED → PAID 반영.
     */
    public PaymentResponseDto confirmPayment(Long guestId, PaymentConfirmRequest request) {
        Payment payment = paymentRepository.findByPgOrderNo(request.getPgOrderNo())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getPayerId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        if (!payment.getAmount().equals(request.getAmount())) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_ALREADY_COMPLETED);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_NOT_PENDING);
        }

        String pgTransactionId = fakePgClient.approvePayment(
                payment.getPgOrderNo(), payment.getAmount(), payment.getId());

        payment.complete(pgTransactionId);
        payment.getMatchRequest().markAsPaidIfAccepted();

        log.info("[Payment] Stub PG 승인 완료 — paymentId={}, pgTransactionId={}, paidAt={}, refundDeadline={}",
                payment.getId(), pgTransactionId, payment.getPaidAt(), payment.getRefundDeadline());
        return PaymentResponseDto.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentForGuest(Long guestId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getMatchRequest().getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        return PaymentResponseDto.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentForGuide(Long guideProfileId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getMatchRequest().getGuideId().equals(guideProfileId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        return PaymentResponseDto.from(payment);
    }

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
