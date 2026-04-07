package com.team6.domain.auth.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.domain.member.service.JwtTokenProvider;
import com.team6.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;

    // 로그인 로직
    public String login(String email, String password) {
        // [LOG] INFO : [Auth-Domain] 로그인 시도(Email : {})
        // 이메일로 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(()->{
                    // [LOG] Warn : 로그인 실패 - 존재하지 않는 이메일
                    System.out.println("로그인 실패 : 등록되지 않는 이메일");
                    return new IllegalArgumentException("이메일 또는 비밀번호를 잘못 입력하였습니다. ");
                });

        // 비밀번호 검증
        // [LOG] DEBUG : [Auth-Domain] 비밀번호 일치 여부 확인 중
        if(!passwordEncoder.matches(password, member.getPassword())) {
            // [LOG] Warn : 로그인 실패 - 비밀번호 불일치
            System.out.println("로그인 실패 : 비밀번호 불일치");
            throw new IllegalArgumentException("이메일 또는 비밀번호를 잘못 입력하였습니다. ");
        }

        // 검증 성공 시 토큰 생성 및 반환
        // [LOG] INFO : [Auth-Domain] 로그인 성공 및 토큰 발행 (Email : {})
        String role = member.getRole().name();
        String token = jwtTokenProvider.createToken(member.getEmail(), role);

        return token;
    }


}
