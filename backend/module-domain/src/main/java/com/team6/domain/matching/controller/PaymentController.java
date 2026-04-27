package com.team6.domain.matching.controller;

import com.team6.domain.matching.dto.request.PaymentConfirmRequest;
import com.team6.domain.matching.dto.request.PaymentCreateRequest;
import com.team6.domain.matching.dto.request.RefundRequestDto;
import com.team6.domain.matching.dto.response.PaymentResponseDto;
import com.team6.domain.matching.dto.response.RefundResponseDto;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.service.PaymentService;
import com.team6.domain.matching.support.MatchingAuthenticationSupport;
import com.team6.domain.member.entity.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/matching/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final MatchingAuthenticationSupport matchingAuthenticationSupport;

    /**
     * 게스트 본인 결제 목록 (마이페이지).
     * {@code GET /matching/payments/me} 는 {@code /{paymentId}} 에 먼저 매칭되어 Long 변환 오류가 날 수 있어 {@code /guest/list} 로 분리한다.
     */
    @GetMapping("/guest/list")
    public ResponseEntity<List<PaymentResponseDto>> listGuestPayments() {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(paymentService.listPaymentsForGuest(guestId));
    }

    @PostMapping
    public ResponseEntity<PaymentResponseDto> createPayment(@RequestBody @Valid PaymentCreateRequest request) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(paymentService.createPayment(guestId, request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponseDto> confirmPayment(@RequestBody @Valid PaymentConfirmRequest request) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(paymentService.confirmPayment(guestId, request));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponseDto> getPayment(@PathVariable Long paymentId) {
        Role tokenRole = matchingAuthenticationSupport.resolveTokenRole();
        if (tokenRole == Role.GUEST) {
            Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
            return ResponseEntity.ok(paymentService.getPaymentForGuest(guestId, paymentId));
        }
        if (tokenRole == Role.GUIDE) {
            Long guideProfileId = matchingAuthenticationSupport.getCurrentGuideProfileId();
            return ResponseEntity.ok(paymentService.getPaymentForGuide(guideProfileId, paymentId));
        }
        throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
    }

    @PostMapping("/refunds")
    public ResponseEntity<RefundResponseDto> requestRefund(@RequestBody @Valid RefundRequestDto request) {
        Long guestId = matchingAuthenticationSupport.getCurrentGuestMemberId();
        return ResponseEntity.ok(paymentService.requestGuestRefund(guestId, request));
    }
}
