package com.team6.domain.member.controller;

import com.team6.domain.member.dto.request.MemberJoinRequest;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/members")
public class MemberController {
    private final MemberService memberService;
    private final SignupEmailVerificationService signupEmailVerificationService;

    // 가입
    @PostMapping("/join")
    public ResponseEntity<Long> join (@Valid @RequestBody MemberJoinRequest requestDto) {
        // [LOG] INFO : [Member-API] 회원가입 요청 수신 (Email : {})

        Long memberId = memberService.join(requestDto.toEntity());

        // [LOG] INFO : [Member-API] 회원가입 성공 (ID : {})
        return ResponseEntity.ok(memberId);
    }

    // 탈퇴
    @DeleteMapping("/me")
    public ResponseEntity<String> withdraw(@RequestParam Role role) {
        memberService.withdraw(role);
        return ResponseEntity.ok("회원 탈퇴가 완료되었습니다. ");
    }

    // 이메일 인증번호 발송
    @PostMapping("/email-verification/send")
    public ResponseEntity<EmailVerificationSendResponse> sendEmailCode(
            @Valid @RequestBody EmailVerificationSendRequest body) {
        int sec = signupEmailVerificationService.sendCode(body.getEmail());
        return ResponseEntity.ok(new EmailVerificationSendResponse(sec));
    }

    // 이메일 인증번호 확인
    @PostMapping("/email-verification/confirm")
    public ResponseEntity<Void> confirmEmailCode(
            @Valid @RequestBody EmailVerificationConfirmRequest body) {
        signupEmailVerificationService.verify(body.getEmail(), body.getCode());
        return ResponseEntity.ok().build();
    }

    // 닉네임 중복 체크
    @GetMapping("/nickname-availability")
    public ResponseEntity<NicknameAvailabilityResponse> nicknameAvailability(
            @RequestParam("nickname") String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        boolean taken = memberService.existsByNickname(nickname.trim());
        return ResponseEntity.ok(new NicknameAvailabilityResponse(!taken));
    }
}
