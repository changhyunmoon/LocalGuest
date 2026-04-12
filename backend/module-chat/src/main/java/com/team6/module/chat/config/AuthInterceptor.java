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
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 1. 연결(CONNECT) 시점에만 토큰 검증 수행
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String bearerToken = accessor.getFirstNativeHeader("Authorization");
            log.info("[WebSocket] CONNECT 요청 수신 - Header: {}", bearerToken);

            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                String token = bearerToken.substring(7);

                if (jwtTokenProvider.validToken(token)) {
                    // 2. 토큰에서 이메일 추출
                    String email = jwtTokenProvider.getEmail(token);

                    // 3. 세션 속성에 저장 (이후 SUBSCRIBE, DISCONNECT 시 활용)
                    if (accessor.getSessionAttributes() != null) {
                        accessor.getSessionAttributes().put("userEmail", email);
                    }
                    log.info("[WebSocket] 인증 성공 - User: {}", email);
                } else {
                    log.error("[WebSocket] 토큰이 유효하지 않습니다.");
                    // Spring Security 의존성 없이 일반 런타임 예외 발생
                    throw new RuntimeException("유효하지 않은 토큰입니다.");
                }
            } else {
                log.error("[WebSocket] Authorization 헤더가 누락되었습니다.");
                throw new RuntimeException("인증 헤더가 없습니다.");
            }
        }

        return message;
    }
}