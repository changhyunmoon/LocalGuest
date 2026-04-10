package com.team6.domain.member.controller;

import com.team6.domain.member.dto.request.MemberJoinRequest;
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

    @PostMapping("/join")
    public ResponseEntity<Long> join (@Valid @RequestBody MemberJoinRequest requestDto) {
        // [LOG] INFO : [Member-API] 회원가입 요청 수신 (Email : {})

        Long memberId = memberService.join(requestDto.toEntity());

        // [LOG] INFO : [Member-API] 회원가입 성공 (ID : {})
        return ResponseEntity.ok(memberId);
    }

    @DeleteMapping("/me")
    public ResponseEntity<String> withdraw() {
        memberService.withdraw();
        return ResponseEntity.ok("회원 탈퇴가 완료되었습니다. ");
    }
}
