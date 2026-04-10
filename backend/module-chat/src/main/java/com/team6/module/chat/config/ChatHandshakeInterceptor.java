package com.team6.module.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            // 클라이언트가 보낸 쿼리 파라미터(?roomId=xxx&userId=123) 추출
            String roomId = servletRequest.getServletRequest().getParameter("roomId");
            String userId = servletRequest.getServletRequest().getParameter("userId");

            if (roomId != null && userId != null) {
                // 웹소켓 세션 어트리뷰트에 저장
                attributes.put("roomId", roomId);
                attributes.put("userId", userId);
                log.info("[Handshake] 유저 {} 번이 방 {} 에 연결을 시도합니다.", userId, roomId);
            }
        }
        return true; // 인증 단계가 없으므로 항상 true
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 핸드쉐이크 이후 로직이 필요 없다면 비워둠
    }
}