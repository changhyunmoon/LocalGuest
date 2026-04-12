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
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private MatchRequestRepository matchRequestRepository;
    @Mock
    private FakePgClient fakePgClient;
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

    @Test
    void 결제생성_성공_PENDING_및_주문번호() {
        Long guestId = 5L;
        Long requestId = 50L;
        MatchRequest mr = acceptedMatchRequest(requestId, guestId, 20L);
        PaymentCreateRequest req = paymentCreate(requestId, 30000, "ACCOMPANY");

        when(matchRequestRepository.findById(requestId)).thenReturn(Optional.of(mr));
        when(paymentRepository.findByMatchRequest_IdAndPaymentType(requestId, "ACCOMPANY"))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            setField(p, "id", 1000L);
            return p;
        });

        PaymentResponseDto dto = paymentService.createPayment(guestId, req);

        assertEquals(1000L, dto.getPaymentId());
        assertEquals(PaymentStatus.PENDING, dto.getStatus());
        assertEquals(30000, dto.getAmount());
        assertEquals("ACCOMPANY", dto.getPaymentType());
        assertEquals(requestId, dto.getRequestId());
        assertEquals(true, dto.getPgOrderNo() != null && dto.getPgOrderNo().startsWith(FakePgClient.STUB_PG_ORDER_PREFIX));
    }

    @Test
    void 결제승인_성공_ACCEPTED가_PAID로() {
        Long guestId = 5L;
        Long requestId = 50L;
        MatchRequest mr = acceptedMatchRequest(requestId, guestId, 20L);
        Payment payment = pendingPayment(400L, guestId, mr, "LM-FAKE-test-order", 25000);

        PaymentConfirmRequest confirm = new PaymentConfirmRequest();
        setField(confirm, "pgOrderNo", "LM-FAKE-test-order");
        setField(confirm, "amount", 25000);

        when(paymentRepository.findByPgOrderNo("LM-FAKE-test-order")).thenReturn(Optional.of(payment));
        when(fakePgClient.approvePayment("LM-FAKE-test-order", 25000, 400L)).thenReturn("FAKE-TXN-400");

        PaymentResponseDto dto = paymentService.confirmPayment(guestId, confirm);

        assertEquals(PaymentStatus.COMPLETED, dto.getStatus());
        assertEquals("FAKE-TXN-400", dto.getPgTransactionId());
        assertEquals(MatchRequestStatus.PAID, mr.getStatus());
        assertEquals(true, dto.getPaidAt() != null && dto.getRefundDeadline() != null);
    }

    @Test
    void 결제승인_금액불일치면_예외() {
        Long guestId = 5L;
        Long requestId = 50L;
        MatchRequest mr = acceptedMatchRequest(requestId, guestId, 20L);
        Payment payment = pendingPayment(401L, guestId, mr, "LM-FAKE-x", 25000);

        PaymentConfirmRequest confirm = new PaymentConfirmRequest();
        setField(confirm, "pgOrderNo", "LM-FAKE-x");
        setField(confirm, "amount", 1);

        when(paymentRepository.findByPgOrderNo("LM-FAKE-x")).thenReturn(Optional.of(payment));

        MatchingException ex = assertThrows(MatchingException.class,
                () -> paymentService.confirmPayment(guestId, confirm));
        assertEquals(MatchingErrorCode.PAYMENT_AMOUNT_MISMATCH, ex.getErrorCode());
    }

    @Test
    void 결제조회_게스트_타인건이면_예외() {
        Payment payment = completedPayment(500L, 99L, LocalDateTime.now().plusMinutes(10));
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(payment));

        MatchingException ex = assertThrows(MatchingException.class,
                () -> paymentService.getPaymentForGuest(10L, 500L));
        assertEquals(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void 결제조회_가이드_본인매칭이면_성공() {
        Long guideProfileId = 20L;
        Long guestId = 5L;
        Long requestId = 50L;
        MatchRequest mr = acceptedMatchRequest(requestId, guestId, guideProfileId);
        Payment payment = completedPayment(600L, guestId, LocalDateTime.now().plusMinutes(10));
        setField(payment, "matchRequest", mr);

        when(paymentRepository.findById(600L)).thenReturn(Optional.of(payment));

        PaymentResponseDto dto = paymentService.getPaymentForGuide(guideProfileId, 600L);
        assertEquals(600L, dto.getPaymentId());
        verify(paymentRepository).findById(600L);
    }

    private MatchRequest acceptedMatchRequest(Long id, Long guestId, Long guideId) {
        MatchRequest mr = MatchRequest.builder()
                .id(id)
                .guestId(guestId)
                .guideId(guideId)
                .destination("Seoul")
                .desiredDate(LocalDate.now())
                .build();
        setField(mr, "status", MatchRequestStatus.ACCEPTED);
        return mr;
    }

    private Payment pendingPayment(Long paymentId, Long payerId, MatchRequest mr, String pgOrderNo, int amount) {
        return Payment.builder()
                .id(paymentId)
                .matchRequest(mr)
                .payerId(payerId)
                .amount(amount)
                .paymentType("ACCOMPANY")
                .pgOrderNo(pgOrderNo)
                .status(PaymentStatus.PENDING)
                .build();
    }

    private PaymentCreateRequest paymentCreate(Long matchRequestId, int amount, String type) {
        PaymentCreateRequest dto = new PaymentCreateRequest();
        setField(dto, "matchRequestId", matchRequestId);
        setField(dto, "amount", amount);
        setField(dto, "paymentType", type);
        return dto;
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
