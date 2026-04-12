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

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // 웹소켓 연결 요청(CONNECT) 시점에만 인증 수행
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
            log.info("[WebSocket] CONNECT 인증 시도...");

            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);

                // 1. JWT 유효성 검증
                if (jwtTokenProvider.validToken(token)) {
                    // 2. 토큰에서 이메일 추출
                    String email = jwtTokenProvider.getEmail(token);

                    // 3. [핵심] 세션 저장소(SessionAttributes)에 이메일 저장
                    // 블루-그린 배포 시 서버가 바뀌어도 '연결된 동안'은 이 메모리 값이 유지됨
                    accessor.getSessionAttributes().put("userEmail", email);

                    log.info("[WebSocket] 인증 성공: userEmail = {}", email);
                } else {
                    log.error("[WebSocket] 인증 실패: 유효하지 않은 토큰");
                    throw new RuntimeException("유효하지 않은 토큰입니다. 다시 로그인해주세요.");
                }
            } else {
                log.error("[WebSocket] 인증 실패: Authorization 헤더 누락");
                throw new RuntimeException("인증 헤더가 누락되었습니다.");
            }
        }

        return message;
    }
}