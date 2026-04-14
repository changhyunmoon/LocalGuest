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
        // [LOG] INFO : [Auth-Domain] JWT 토큰 생성 시작 (Target : {})
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_VALIDITY))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
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
}
