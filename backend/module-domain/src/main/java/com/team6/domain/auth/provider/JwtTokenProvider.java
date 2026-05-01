package com.team6.domain.auth.provider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String secretKey;

    private Key key;
    private final long ACCESS_TOKEN_VALIDITY = 1000L*60*60;   // 1시간 유효
    private final long REFRESH_TOKEN_VALIDITY = 1000L*60*60*24*7;  // 7일

    @PostConstruct
    protected void init() {
        // [LOG] INFO : [Auth-Domain] SecretKey를 기반으로 암호화 키 생성
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String createToken(String email, String role) {
        return buildAccessToken(email, role, false);
    }

    /**
     * 최초 소셜(게스트) 가입 직후 여행 성향(온보딩) 유도 — 프론트가 claim으로 분기
     */
    public String createToken(String email, String role, boolean onboardingClaim) {
        return buildAccessToken(email, role, onboardingClaim);
    }

    private String buildAccessToken(String email, String role, boolean onboardingClaim) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_VALIDITY));
        if (onboardingClaim) {
            builder.claim("onboarding", true);
        }
        return builder.signWith(key).compact();
    }

    public String createRefreshToken(String email, String role) {
        Date now = new Date();

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_VALIDITY))
                .signWith(key)
                .compact();
    }

    public boolean validToken(String token) {
        try{
            Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token);
            return true;
        }catch (Exception e) {
            //[LOG] Warn : 유효하지 않은 토큰입니다.
            return false;

        }
    }

    // 이메일 추출
    public String getEmail(String token) {
        return getClaims(token).getSubject();
    }

    // 역할 추출
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }


    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpiration(String token) {
        Date expiration = getClaims(token).getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }
}
