package com.team6.module.chat.config;

import com.team6.domain.auth.provider.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // accessor가 null이거나 command가 없는 경우는 통과 (Heartbeat 등)
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");

        // 1. 토큰 존재 여부 확인
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            log.error("WebSocket 인증 헤더가 누락되었습니다.");
            throw new AccessDeniedException("WebSocket JWT가 없습니다.");
        }

        String token = authorization.substring(BEARER_PREFIX.length());

        // 2. 토큰 유효성 검증
        if (!jwtTokenProvider.validToken(token)) {
            log.error("유효하지 않은 WebSocket 토큰입니다.");
            throw new AccessDeniedException("유효하지 않은 WebSocket JWT입니다.");
        }

        // 3. 유저 정보 추출 및 Authentication 객체 생성
        String userEmail = jwtTokenProvider.getEmail(token);

        // Spring Security의 Authentication 인터페이스 구현체를 생성합니다.
        Authentication  authentication = new UsernamePasswordAuthenticationToken(
                userEmail,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // 4. 세션에 사용자 정보 저장
        accessor.setUser(authentication);
        log.info("WebSocket 인증 성공: userEmail = {}", userEmail);
    }
}