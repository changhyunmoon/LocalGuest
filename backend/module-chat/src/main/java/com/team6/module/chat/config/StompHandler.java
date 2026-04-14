package com.team6.module.chat.config;

import com.team6.domain.auth.provider.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        System.out.println("message:" + message);
        System.out.println("헤더 : " + message.getHeaders());
        System.out.println("토큰" + accessor.getNativeHeader("Authorization"));
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                // 1. 토큰 유효성 검증
                jwtTokenProvider.validToken(token);

                // 2. 토큰에서 이메일(또는 Subject) 추출
                // jwtTokenProvider에 이메일을 추출하는 메서드가 있다고 가정합니다.
                String userEmail = jwtTokenProvider.getEmail(token);

                // 3. 세션 속성(Session Attributes)에 저장 ⭐️ (핵심)
                // 이렇게 저장해둬야 이후 SUBSCRIBE, DISCONNECT 시점에 꺼내 쓸 수 있습니다.
                Objects.requireNonNull(accessor.getSessionAttributes()).put("userEmail", userEmail);

                log.info("[StompHandler] User {} connected and session attributes set.", userEmail);
            }
        }
        return message;
    }
}