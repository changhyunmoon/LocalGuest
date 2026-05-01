package com.team6.module.chat.controller;

import com.team6.module.chat.dto.request.ChatMessageSendRequest;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import com.team6.module.chat.service.ChatMessageService;
import com.team6.module.chat.service.RedisPubService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final RedisPubService redisPubService;
    private final ChatMessageService chatMessageService;

    /**
     * WebSocket(STOMP) 메시지 핸들러
     * 클라이언트가 /pub/chat/message로 보낼 때 사용
     */
    @MessageMapping("/chat/message")
    public void message(ChatMessageSendRequest request, Authentication authentication) {

        ChatMessageResponse savedMessage =
                chatMessageService.saveAndPublishReadyMessage(request, authentication.getName());
        //메시지 전송
        redisPubService.sendMessage(savedMessage);
    }

}