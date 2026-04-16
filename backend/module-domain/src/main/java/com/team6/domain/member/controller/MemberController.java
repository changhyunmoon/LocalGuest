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
}
