package com.team6.domain.matching.service;

import com.team6.domain.matching.dto.request.RefundRequestDto;
import com.team6.domain.matching.dto.response.RefundResponseDto;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.Payment;
import com.team6.domain.matching.entity.Refund;
import com.team6.domain.matching.entity.enums.PaymentStatus;
import com.team6.domain.matching.entity.enums.RefundType;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.PaymentRepository;
import com.team6.domain.matching.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @InjectMocks
    private PaymentService paymentService;

    @Test
    void 게스트_환불요청_성공() {
        Long guestId = 10L;
        Long paymentId = 100L;

        Payment payment = completedPayment(paymentId, guestId, LocalDateTime.now().plusMinutes(30));
        RefundRequestDto request = refundRequest(paymentId, "가이드 불만족", "https://evidence.local/1");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPayment_Id(paymentId)).thenReturn(Optional.empty());
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            setField(refund, "id", 999L);
            return refund;
        });

        RefundResponseDto response = paymentService.requestGuestRefund(guestId, request);

        assertEquals(999L, response.getRefundId());
        assertEquals(paymentId, response.getPaymentId());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    void 환불요청_타인결제면_권한예외() {
        Payment payment = completedPayment(200L, 777L, LocalDateTime.now().plusMinutes(30));
        RefundRequestDto request = refundRequest(200L, "사유", null);
        when(paymentRepository.findById(200L)).thenReturn(Optional.of(payment));

        MatchingException ex = assertThrows(MatchingException.class,
                () -> paymentService.requestGuestRefund(10L, request));
        assertEquals(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void 환불요청_마감초과면_예외() {
        Long paymentId = 300L;
        Payment payment = completedPayment(paymentId, 10L, LocalDateTime.now().minusSeconds(1));
        RefundRequestDto request = refundRequest(paymentId, "사유", null);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        MatchingException ex = assertThrows(MatchingException.class,
                () -> paymentService.requestGuestRefund(10L, request));
        assertEquals(MatchingErrorCode.REFUND_DEADLINE_EXCEEDED, ex.getErrorCode());
    }

    private Payment completedPayment(Long paymentId, Long payerId, LocalDateTime refundDeadline) {
        MatchRequest matchRequest = MatchRequest.builder()
                .id(1L)
                .guestId(payerId)
                .guideId(2L)
                .destination("Seoul")
                .desiredDate(LocalDate.now())
                .build();
        return Payment.builder()
                .id(paymentId)
                .matchRequest(matchRequest)
                .payerId(payerId)
                .amount(10000)
                .paymentType("ACCOMPANY")
                .pgOrderNo("ORDER-" + paymentId)
                .status(PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now().minusMinutes(10))
                .refundDeadline(refundDeadline)
                .build();
    }

    private RefundRequestDto refundRequest(Long paymentId, String reason, String evidenceUrl) {
        RefundRequestDto dto = new RefundRequestDto();
        setField(dto, "paymentId", paymentId);
        setField(dto, "reason", reason);
        setField(dto, "evidenceUrl", evidenceUrl);
        return dto;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
