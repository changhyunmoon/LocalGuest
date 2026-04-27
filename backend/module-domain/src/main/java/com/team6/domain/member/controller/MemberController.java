package com.team6.domain.member.controller;

import com.team6.domain.member.dto.request.*;
import com.team6.domain.member.dto.response.MemberProfileResponse;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.service.AccountRecoveryService;
import com.team6.domain.member.service.MemberService;
import com.team6.domain.member.service.SignupEmailVerificationService;
import com.team6.domain.member.dto.response.EmailVerificationSendResponse;
import com.team6.domain.member.dto.response.FindIdResponse;
import com.team6.domain.member.dto.response.NicknameAvailabilityResponse;
import com.team6.module.common.global.exception.ExceptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/members")
public class MemberController {
    private final MemberService memberService;
    private final SignupEmailVerificationService signupEmailVerificationService;
    private final AccountRecoveryService accountRecoveryService;

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

    /** 가입 시 입력한 이름·닉네임·역할로 로그인 아이디(이메일) 일부를 확인합니다. */
    @PostMapping("/find-id")
    public ResponseEntity<?> findLoginId(@Valid @RequestBody FindIdRequest body) {
        try {
            FindIdResponse res = accountRecoveryService.findMaskedEmail(body.getName(), body.getNickname(), body.getRole());
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ExceptionResponse.of(HttpStatus.BAD_REQUEST, "FIND_ID_FAILED", e.getMessage()));
        }
    }

    // 비밀번호 재설정 코드 발송
    @PostMapping("/password-reset/send")
    public ResponseEntity<?> sendPasswordResetCode(@Valid @RequestBody PasswordResetSendRequest body) {
        try {
            int sec = accountRecoveryService.sendPasswordResetCode(body.getEmail(), body.getRole());
            return ResponseEntity.ok(new EmailVerificationSendResponse(sec));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ExceptionResponse.of(HttpStatus.BAD_REQUEST, "PWD_RESET_SEND_FAILED", e.getMessage()));
        }
    }

    // 비밀번호 재설정 코드 확인
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest body) {
        try {
            accountRecoveryService.resetPasswordAfterVerification(
                    body.getEmail(),
                    body.getRole(),
                    body.getCode(),
                    body.getNewPassword()
            );
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ExceptionResponse.of(HttpStatus.BAD_REQUEST, "PWD_RESET_CONFIRM_FAILED", e.getMessage()));
        }
    }

    // 프로필 이미지 조회
    @GetMapping("/me/profile")
    public ResponseEntity<MemberProfileResponse> getMyProfile() {
        Member member = memberService.getCurrentMember();
        return ResponseEntity.ok(MemberProfileResponse.from(member));
    }

    // 프로필 이미지 업데이트
    @PutMapping("/me/profile")
    public ResponseEntity<MemberProfileResponse> updateMyProfile(
            @RequestBody(required = false) MemberProfileImageUpdateRequest body) {
        String url = body != null ? body.getProfileImageUrl() : null;
        // 예: null = 유지, "" = 삭제
        if (url == null) {
            return ResponseEntity.ok(MemberProfileResponse.from(memberService.getCurrentMember()));
        }
        memberService.updateMyProfileImage(url, true);
        return ResponseEntity.ok(MemberProfileResponse.from(memberService.getCurrentMember()));
    }
}
