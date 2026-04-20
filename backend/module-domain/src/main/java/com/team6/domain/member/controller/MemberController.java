package com.team6.domain.member.controller;

import com.team6.domain.member.dto.request.MemberJoinRequest;
import com.team6.domain.member.dto.request.GuestProfileUpdateRequest;
import com.team6.domain.member.dto.request.TravelPreferenceUpdateRequest;
import com.team6.domain.member.dto.response.GuestProfileResponse;
import com.team6.domain.member.service.MemberService;
import com.team6.domain.member.service.SignupEmailVerificationService;
import com.team6.domain.member.dto.request.EmailVerificationSendRequest;
import com.team6.domain.member.dto.request.EmailVerificationConfirmRequest;
import com.team6.domain.member.dto.response.EmailVerificationSendResponse;
import com.team6.domain.member.dto.response.NicknameAvailabilityResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;
    private final SignupEmailVerificationService signupEmailVerificationService;

    // ✅ 회원가입 수정
    @PostMapping("/join")
    public ResponseEntity<Long> join(@Valid @RequestBody MemberJoinRequest request) {
        // ✅ MemberJoinRequest를 직접 전달 (toEntity() 제거)
        Long memberId = memberService.join(request);

        return ResponseEntity.ok(memberId);
    }

    // ✅ 회원 탈퇴 수정 (role 파라미터 제거)
    @DeleteMapping("/me")
    public ResponseEntity<String> withdraw() {
        // ✅ SecurityUtil에서 자동으로 email 추출
        memberService.withdraw();

        return ResponseEntity.ok("회원 탈퇴가 완료되었습니다.");
    }

    // ✅ Guest 프로필 조회 (신규)
    @GetMapping("/guest-profile")
    public ResponseEntity<GuestProfileResponse> getGuestProfile(Authentication authentication) {
        Long memberId = Long.parseLong(authentication.getName());

        GuestProfileResponse response = memberService.getGuestProfile(memberId);

        return ResponseEntity.ok(response);
    }

    // ✅ Guest 프로필 업데이트 (신규)
    @PutMapping("/guest-profile")
    public ResponseEntity<Void> updateGuestProfile(
            @Valid @RequestBody GuestProfileUpdateRequest request,
            Authentication authentication) {

        Long memberId = Long.parseLong(authentication.getName());

        memberService.updateGuestProfile(memberId, request);

        return ResponseEntity.ok().build();
    }

    // ✅ 여행 선호도 업데이트 (신규)
    @PutMapping("/guest-profile/travel-preference")
    public ResponseEntity<Void> updateTravelPreference(
            @Valid @RequestBody TravelPreferenceUpdateRequest request,
            Authentication authentication) {

        Long memberId = Long.parseLong(authentication.getName());

        memberService.updateTravelPreference(memberId, request);

        return ResponseEntity.ok().build();
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