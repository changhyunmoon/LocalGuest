package com.team6.module.chat.controller;

import com.team6.module.chat.dto.chatMessage.ChatMessageRequest;
import com.team6.module.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageStompController {

    private final ChatMessageService chatMessageService;


    @MessageMapping("/chat/message")
    public void message(ChatMessageRequest request) {
        log.info("[WebSocket] 메시지 수신 - Room: {}, Sender: {}", request.roomId(), request.senderEmail());

        // ChatMessageService에서 Redis 인원 파악, DB 저장, 메시지 발행(send)을 모두 처리합니다.
        chatMessageService.sendMessage(request);
    }

}