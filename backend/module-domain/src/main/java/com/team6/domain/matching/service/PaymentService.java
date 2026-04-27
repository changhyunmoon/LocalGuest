package com.team6.domain.matching.service;

import com.team6.domain.matching.client.FakePgClient;
import com.team6.domain.matching.client.GuideScheduleSyncClient;
import com.team6.domain.matching.client.KakaoPayClient;
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
import com.team6.domain.matching.entity.enums.TourExtensionStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.repository.PaymentRepository;
import com.team6.domain.matching.repository.RefundRepository;
import com.team6.domain.matching.repository.TourExtensionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
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
    private final KakaoPayClient kakaoPayClient;
    private final GuideScheduleSyncClient guideScheduleSyncClient;
    private final TourExtensionRepository tourExtensionRepository;

    @Value("${matching.payment.provider:fake}")
    private String paymentProvider;

    /**
     * 결제 요청 생성 — PENDING, Stub PG 주문번호 발급.
     * 매칭 요청이 게스트 소유이며 PENDING/ACCEPTED 일 때 가능.
     * 동일 (match_request_id, payment_type) 재요청은 기존 결제를 재사용해 멱등 처리한다.
     */
    public PaymentResponseDto createPayment(Long guestId, PaymentCreateRequest request) {
        MatchRequest matchRequest = matchRequestRepository.findById(request.getMatchRequestId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        if (!matchRequest.getGuestId().equals(guestId)) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }

        if (isExtensionPayment(request.getPaymentType())) {
            validateExtensionPaymentRequest(matchRequest, request);
        } else if (matchRequest.getStatus() != MatchRequestStatus.PENDING
                && matchRequest.getStatus() != MatchRequestStatus.ACCEPTED) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }

        Payment existing = paymentRepository
                .findByMatchRequest_IdAndPaymentType(matchRequest.getId(), request.getPaymentType())
                .orElse(null);
        if (existing != null) {
            log.info("[Payment] 기존 결제 재사용 — paymentId={}, requestId={}, guestId={}, status={}, type={}",
                    existing.getId(), matchRequest.getId(), guestId, existing.getStatus(), request.getPaymentType());
            if (existing.getStatus() == PaymentStatus.PENDING && isKakaoProvider()) {
                String partnerUserId = String.valueOf(guestId);
                String itemName = "LocalGuest " + existing.getPaymentType();
                KakaoPayClient.ReadyResult readyResult = kakaoPayClient.ready(
                        existing.getPgOrderNo(),
                        partnerUserId,
                        itemName,
                        existing.getAmount()
                );
                existing.storePgTransactionId(readyResult.tid());
                return PaymentResponseDto.from(existing, readyResult.redirectUrl());
            }
            if (existing.getStatus() != PaymentStatus.PENDING
                    && existing.getStatus() != PaymentStatus.COMPLETED) {
                throw new MatchingException(MatchingErrorCode.PAYMENT_NOT_PENDING);
            }
            return PaymentResponseDto.from(existing);
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
        if (isKakaoProvider()) {
            String partnerUserId = String.valueOf(guestId);
            String itemName = "LocalGuest " + request.getPaymentType();
            KakaoPayClient.ReadyResult readyResult = kakaoPayClient.ready(
                    saved.getPgOrderNo(),
                    partnerUserId,
                    itemName,
                    saved.getAmount()
            );
            saved.storePgTransactionId(readyResult.tid());
            return PaymentResponseDto.from(saved, readyResult.redirectUrl());
        }
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
            log.info("[Payment] 이미 완료된 결제 재확인 요청 수신 — paymentId={}, pgOrderNo={}",
                    payment.getId(), payment.getPgOrderNo());
            return PaymentResponseDto.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_NOT_PENDING);
        }

        String pgTransactionId;
        if (isKakaoProvider()) {
            if (request.getPgToken() == null || request.getPgToken().isBlank()) {
                throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
            }
            if (payment.getPgTransactionId() == null || payment.getPgTransactionId().isBlank()) {
                throw new MatchingException(MatchingErrorCode.PAYMENT_PG_VERIFICATION_FAILED);
            }
            KakaoPayClient.ApproveResult approveResult = kakaoPayClient.approve(
                    payment.getPgTransactionId(),
                    payment.getPgOrderNo(),
                    String.valueOf(guestId),
                    request.getPgToken()
            );
            pgTransactionId = approveResult.aid();
        } else {
            pgTransactionId = fakePgClient.approvePayment(
                    payment.getPgOrderNo(), payment.getAmount(), payment.getId());
        }

        payment.complete(pgTransactionId);
        payment.getMatchRequest().markAsPaidIfAcceptedOrPending();
        completeExtensionIfNeeded(payment);
        syncGuideSchedulePaidSafely(payment);

        log.info("[Payment] Stub PG 승인 완료 — paymentId={}, pgTransactionId={}, paidAt={}, refundDeadline={}",
                payment.getId(), pgTransactionId, payment.getPaidAt(), payment.getRefundDeadline());
        return PaymentResponseDto.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> listPaymentsForGuest(Long guestId) {
        return paymentRepository.findByPayerId(guestId).stream()
                .sorted(Comparator
                        .comparing(Payment::getPaidAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparing(Payment::getId).reversed()))
                .map(PaymentResponseDto::from)
                .toList();
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

    private boolean isKakaoProvider() {
        return "kakao".equalsIgnoreCase(paymentProvider);
    }

    private boolean isExtensionPayment(String paymentType) {
        return "EXTENSION".equalsIgnoreCase(paymentType);
    }

    private void validateExtensionPaymentRequest(MatchRequest matchRequest, PaymentCreateRequest request) {
        if (matchRequest.getStatus() != MatchRequestStatus.ACCEPTED
                && matchRequest.getStatus() != MatchRequestStatus.PAID
                && matchRequest.getStatus() != MatchRequestStatus.IN_PROGRESS
                && matchRequest.getStatus() != MatchRequestStatus.COMPLETED) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        var extension = tourExtensionRepository.findByMatchRequest_Id(matchRequest.getId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.TOUR_EXTENSION_NOT_FOUND));
        if (extension.getStatus() != TourExtensionStatus.GUIDE_APPROVED) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        if (extension.getExtendedPrice() == null || !extension.getExtendedPrice().equals(request.getAmount())) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private void completeExtensionIfNeeded(Payment payment) {
        if (!isExtensionPayment(payment.getPaymentType())) {
            return;
        }
        var extension = tourExtensionRepository.findByMatchRequest_Id(payment.getMatchRequest().getId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.TOUR_EXTENSION_NOT_FOUND));
        extension.completePayByGuestSelection();
        log.info("[F05-03] 연장 결제 완료 반영 — requestId={}, paymentId={}, extensionId={}",
                payment.getMatchRequest().getId(), payment.getId(), extension.getId());
    }

    /**
     * 가이드 스케줄 paid-confirm 연동 실패는 결제 자체를 롤백시키지 않는다.
     * PG 승인 성공 후 결제 기록 유실(금전-DB 불일치)을 막기 위한 방어 처리다.
     */
    private void syncGuideSchedulePaidSafely(Payment payment) {
        Long guideId = payment.getMatchRequest().getGuideId();
        Long scheduleId = payment.getMatchRequest().getGuideScheduleId();
        try {
            guideScheduleSyncClient.confirmPaid(guideId, scheduleId, null);
        } catch (MatchingException e) {
            log.error("[F03-06] 결제 완료 후 가이드 스케줄 paid-confirm 동기화 실패 — paymentId={}, requestId={}, guideId={}, scheduleId={}, errorCode={}",
                    payment.getId(),
                    payment.getMatchRequest().getId(),
                    guideId,
                    scheduleId,
                    e.getErrorCode(),
                    e);
        } catch (Exception e) {
            log.error("[F03-06] 결제 완료 후 가이드 스케줄 paid-confirm 동기화 실패(예상치못한예외) — paymentId={}, requestId={}, guideId={}, scheduleId={}",
                    payment.getId(),
                    payment.getMatchRequest().getId(),
                    guideId,
                    scheduleId,
                    e);
        }
    }
}
