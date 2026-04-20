package com.team6.domain.auth.provider;

import com.team6.domain.member.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    private Key key;
    private final long ACCESS_TOKEN_VALIDITY = 1000L * 60 * 60;  // 1시간
    private final long REFRESH_TOKEN_VALIDITY = 1000L * 60 * 60 * 24 * 7;  // 7일

    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    // ✅ Set<Role>을 받도록 수정
    public String createToken(String email, Set<Role> roles) {
        Date now = new Date();

        // ✅ 여러 역할을 List<String>으로 변환
        List<String> roleNames = roles.stream()
                .map(Role::name)
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(email)
                .claim("roles", roleNames)  // ✅ "roles" (복수)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_VALIDITY))
                .signWith(key)
                .compact();
    }

    // ✅ Refresh Token도 roles 사용
    public String createRefreshToken(String email, Set<Role> roles) {
        Date now = new Date();

        List<String> roleNames = roles.stream()
                .map(Role::name)
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(email)
                .claim("roles", roleNames)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_VALIDITY))
                .signWith(key)
                .compact();
    }

    public boolean validToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith((SecretKey) key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ✅ JWT에서 Authentication 생성 (여러 역할 처리)
    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);
        String email = claims.getSubject();

        // ✅ roles를 List로 파싱
        List<String> roleNames = claims.get("roles", List.class);

        // ✅ GrantedAuthority로 변환
        Collection<GrantedAuthority> authorities = roleNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UserDetails userDetails = new User(email, "", authorities);

        return new UsernamePasswordAuthenticationToken(userDetails, "", authorities);
    }

    public String getEmail(String token) {
        return getClaims(token).getSubject();
    }

    // ✅ 단일 역할 조회 (하위 호환성 유지)
    public String getRole(String token) {
        List<String> roles = getRoles(token);
        return roles.isEmpty() ? null : roles.get(0);
    }

    // ✅ 모든 역할 조회
    public List<String> getRoles(String token) {
        return getClaims(token).get("roles", List.class);
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