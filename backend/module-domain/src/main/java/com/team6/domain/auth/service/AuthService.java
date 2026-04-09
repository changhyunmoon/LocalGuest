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
    private final JwtTokenProvider jwtTokenProvider;

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
        member.validatePassword(passwordEncoder, password);

        // 검증 성공 시 토큰 생성 및 반환
        // [LOG] INFO : [Auth-Domain] 로그인 성공 및 토큰 발행 (Email : {})
        String role = member.getRole().name();
        return jwtTokenProvider.createToken(member.getEmail(), role);
    }

    @Transactional
    public void logout(String accessToken) {
        // 1. 토큰에서 만료 시간 등을 추출 (jwtTokenProvider 이용)
        // 2. Redis에 'logout:토큰값' 형식으로 저장하여 남은 시간 동안 접근 차단
        // 지금은 Redis 설정 전이므로 로그와 함께 추후 구현 주석 남김

        // [LOG] INFO : 로그아웃 요청 처리 중
        if (accessToken == null || !accessToken.startsWith("Bearer ")) {
            throw new IllegalArgumentException("유효하지 않은 토큰 형식입니다.");
        }

        String actualToken = accessToken.substring(7);

        // TODO: redisTemplate.opsForValue().set(actualToken, "logout", expiration, TimeUnit.MILLISECONDS);
        System.out.println("서버 측 로그아웃 처리 완료 (Blacklist 등록 대기): " + actualToken);
    }


}
