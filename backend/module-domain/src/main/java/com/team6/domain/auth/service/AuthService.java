package com.team6.domain.auth.service;

import com.team6.domain.auth.dto.LoginRequest;
import com.team6.domain.member.dto.response.TokenResponse;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.domain.auth.provider.JwtTokenProvider;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    // 로그인 로직
    public TokenResponse login(LoginRequest request) {
        // [LOG] INFO : [Auth-Domain] 로그인 시도(Email : {})
        // 이메일로 회원 조회
        Member member = memberRepository.findByEmailAndRole(request.getEmail(), request.getRole())
                .orElseThrow(()->{
                    // [LOG] Warn : 로그인 실패 - 존재하지 않는 이메일
                    return new IllegalArgumentException("이메일 또는 비밀번호를 잘못 입력하였습니다. ");
                });

        // 비밀번호 검증
        // [LOG] DEBUG : [Auth-Domain] 비밀번호 일치 여부 확인 중
        member.validatePassword(passwordEncoder, request.getPassword());

        // 검증 성공 시 토큰 생성 및 반환
        // [LOG] INFO : [Auth-Domain] 로그인 성공 및 토큰 발행 (Email : {})
        String role = member.getRole().name();
        String accessToken = jwtTokenProvider.createToken(member.getEmail(), role);
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getEmail(), role);

        // Redis 저장
        redisTemplate.opsForValue().set("RT:" + member.getRole() + ":" + member.getEmail(),
                refreshToken, 7, TimeUnit.DAYS);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .grantType("Bearer")
                .build();
    }

    @Transactional
    public void logout(String accessToken) {
        // 1. 토큰에서 만료 시간 등을 추출 (jwtTokenProvider 이용)
        // 2. Redis에 'logout:토큰값' 형식으로 저장하여 남은 시간 동안 접근 차단
        // 지금은 Redis 설정 전이므로 로그와 함께 추후 구현 주석 남김

        // [LOG] INFO : 로그아웃 요청 처리 중
        if (!jwtTokenProvider.validToken(accessToken)) {
            throw new IllegalArgumentException("유효하지 않은 토큰 형식입니다.");
        }

        String email = jwtTokenProvider.getEmail(accessToken);
        String role = jwtTokenProvider.getRole(accessToken);

        //refresh Token 삭제
        redisTemplate.delete("RT:" + role + ":" + email);

        //Access Token 블랙리스트 등록
        long expiration = jwtTokenProvider.getExpiration(accessToken);
        if(expiration > 0) {
            redisTemplate.opsForValue().set(
                    "BL:" + accessToken,
                    "logout",
                    expiration,
                    TimeUnit.MILLISECONDS
            );
        }

        // 현재 시큐리티 컨텍스트 초기화
        SecurityContextHolder.clearContext();
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        // refresh Token 유효성 검증
        if (!jwtTokenProvider.validToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다. ");
        }

        // Redis에서 저장된 Refresh Token과 비교
        String email = jwtTokenProvider.getEmail(refreshToken);
        String roleStr = jwtTokenProvider.getRole(refreshToken);

        if(roleStr == null) {
            throw new IllegalArgumentException("토큰에 권한 정보가 누락되었습니다. ");
        }

        String savedToken = redisTemplate.opsForValue().get("RT:" + roleStr + ":" + email);
        if(!refreshToken.equals(savedToken)) {
            throw new IllegalArgumentException("Refresh Token이 일치하지 않습니다. ");
        }

        // 새 Access Token 발급
        Member member = memberRepository.findByEmailAndRole(email, Role.valueOf(roleStr))
                .orElseThrow(()-> new IllegalArgumentException("사용자를 찾을 수 없습니다. "));
        String newAccessToken = jwtTokenProvider.createToken(email, roleStr);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .grantType("Bearer")
                .refreshToken(refreshToken)
                .build();
    }


}
