package com.team6.apiserver.auth.filter;

import com.team6.domain.auth.provider.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        // 💡 웹소켓 핸드셰이크 요청(/ws-stomp)은 JWT 검사 없이 통과시킵니다.
        String path = request.getRequestURI();
        if (path.startsWith("/ws-stomp")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 헤더에서 토큰 추출
        String token = resolveToken(request);

        // 토큰이 유효하면 유저 정보를 시큐리티 세션에 담음
        if(token != null && jwtTokenProvider.validToken(token)) {
            // 블랙리스트 확인(로그아웃된 토큰)
            String blacklisted = redisTemplate.opsForValue().get("BL:" + token);
            if(blacklisted != null) {
                // 블랙리스트에 있는 토큰은 무시
                filterChain.doFilter(request, response);
                return;
            }

            // 토큰에서 사용자 정보 추출 → Authentication 생성
            Authentication authentication = jwtTokenProvider.getAuthentication(token);

            // 시큐리티 보관함에 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/oauth2/") || path.startsWith("/login/");
    }
}
