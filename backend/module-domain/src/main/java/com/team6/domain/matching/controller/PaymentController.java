package com.team6.domain.matching.controller;

import com.team6.domain.matching.dto.request.RefundRequestDto;
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
