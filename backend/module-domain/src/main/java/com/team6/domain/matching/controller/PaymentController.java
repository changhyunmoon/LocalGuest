package com.team6.domain.matching.controller;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.dto.request.PaymentConfirmRequest;
import com.team6.domain.matching.dto.request.PaymentCreateRequest;
import com.team6.domain.matching.dto.request.RefundRequestDto;
import com.team6.domain.matching.dto.response.PaymentResponseDto;
import com.team6.domain.matching.dto.response.RefundResponseDto;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.service.PaymentService;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.common.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/matching/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final MemberRepository memberRepository;
    private final GuideProfileRepository guideProfileRepository;

    @PostMapping
    public ResponseEntity<PaymentResponseDto> createPayment(@RequestBody @Valid PaymentCreateRequest request) {
        Long guestId = getCurrentMemberId(Role.GUEST);
        return ResponseEntity.ok(paymentService.createPayment(guestId, request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponseDto> confirmPayment(@RequestBody @Valid PaymentConfirmRequest request) {
        Long guestId = getCurrentMemberId(Role.GUEST);
        return ResponseEntity.ok(paymentService.confirmPayment(guestId, request));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponseDto> getPayment(@PathVariable Long paymentId) {
        Member member = memberRepository.findByEmail(SecurityUtil.getCurrentUserEmail())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
        if (member.getRole() == Role.GUEST) {
            return ResponseEntity.ok(paymentService.getPaymentForGuest(member.getId(), paymentId));
        }
        if (member.getRole() == Role.GUIDE) {
            Long guideProfileId = guideProfileRepository.findByMemberId(member.getId())
                    .map(guideProfile -> guideProfile.getId())
                    .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
            return ResponseEntity.ok(paymentService.getPaymentForGuide(guideProfileId, paymentId));
        }
        throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
    }

    @PostMapping("/refunds")
    public ResponseEntity<RefundResponseDto> requestRefund(@RequestBody @Valid RefundRequestDto request) {
        Long guestId = getCurrentMemberId(Role.GUEST);
        return ResponseEntity.ok(paymentService.requestGuestRefund(guestId, request));
    }

    private Long getCurrentMemberId(Role requiredRole) {
        String email = SecurityUtil.getCurrentUserEmail();
        return memberRepository.findByEmail(email)
                .map(member -> validateAndGetMemberId(member, requiredRole))
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED));
    }

    private Long validateAndGetMemberId(Member member, Role requiredRole) {
        if (requiredRole != null && member.getRole() != requiredRole) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
        }
        return member.getId();
    }
}
