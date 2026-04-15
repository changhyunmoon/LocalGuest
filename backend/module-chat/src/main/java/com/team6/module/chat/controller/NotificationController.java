package com.team6.module.chat.controller;

import com.team6.domain.auth.provider.JwtTokenProvider;
import com.team6.module.chat.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * SSE 구독 엔드포인트
     * @param token 프론트엔드 EventSource에서 보낸 쿼리 파라미터 토큰
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam("token") String token) {

        jwtTokenProvider.validToken(token);

        String userEmail = jwtTokenProvider.getEmail(token);

        log.info("[SSE Subscribe] User Email: {}", userEmail);

        return notificationService.subscribe(userEmail);
    }
}