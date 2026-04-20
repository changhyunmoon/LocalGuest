package com.team6.domain.auth.service;

import com.team6.domain.auth.dto.LoginRequest;
import com.team6.domain.member.dto.response.TokenResponse;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.domain.auth.provider.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
    }

    // ✅ 로그인 로직 수정
    public TokenResponse login(LoginRequest request) {
        // ✅ email로만 조회 (role 조건 제거)
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new IllegalArgumentException("이메일 또는 비밀번호를 잘못 입력하였습니다.")
                );

        // 비밀번호 검증
        member.validatePassword(passwordEncoder, request.getPassword());

        // 계정 상태 확인
        if (member.getStatus() == Status.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 회원입니다.");
        }

        // ✅ Set<Role>로 토큰 생성
        String accessToken = jwtTokenProvider.createToken(member.getEmail(), member.getRoles());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getEmail(), member.getRoles());

        // ✅ Redis 저장 (email만 사용)
        redisTemplate.opsForValue().set(
                "RT:" + member.getEmail(),
                refreshToken,
                7,
                TimeUnit.DAYS
        );

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .grantType("Bearer")
                .build();
    }

    // ✅ 로그아웃 로직 수정
    @Transactional
    public void logout(String accessToken) {
        if (!jwtTokenProvider.validToken(accessToken)) {
            throw new IllegalArgumentException("유효하지 않은 토큰 형식입니다.");
        }

        String email = jwtTokenProvider.getEmail(accessToken);

        // ✅ Refresh Token 삭제 (email만 사용)
        redisTemplate.delete("RT:" + email);

        // Access Token 블랙리스트 등록
        long expiration = jwtTokenProvider.getExpiration(accessToken);
        if (expiration > 0) {
            redisTemplate.opsForValue().set(
                    "BL:" + accessToken,
                    "logout",
                    expiration,
                    TimeUnit.MILLISECONDS
            );
        }

        // 시큐리티 컨텍스트 초기화
        SecurityContextHolder.clearContext();
    }

    // ✅ 토큰 재발급 로직 수정
    @Transactional
    public TokenResponse reissue(String refreshToken) {
        // Refresh Token 유효성 검증
        if (!jwtTokenProvider.validToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }

        // 토큰에서 정보 추출
        String email = jwtTokenProvider.getEmail(refreshToken);
        List<String> roles = jwtTokenProvider.getRoles(refreshToken);

        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("토큰에 권한 정보가 누락되었습니다.");
        }

        // ✅ Redis에서 저장된 Refresh Token 확인 (email만 사용)
        String savedToken = redisTemplate.opsForValue().get("RT:" + email);
        if (!refreshToken.equals(savedToken)) {
            throw new IllegalArgumentException("Refresh Token이 일치하지 않습니다.");
        }

        // ✅ 회원 조회 (email로만)
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 계정 상태 확인
        if (member.getStatus() == Status.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 회원입니다.");
        }

        // ✅ 새 Access Token 발급 (Set<Role> 사용)
        String newAccessToken = jwtTokenProvider.createToken(email, member.getRoles());

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .grantType("Bearer")
                .build();
    }
}