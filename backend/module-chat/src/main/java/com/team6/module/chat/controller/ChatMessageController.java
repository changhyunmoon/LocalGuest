package com.team6.module.chat.controller;


import com.team6.module.chat.dto.request.ChatMessageRequest;
import com.team6.module.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/chat/message")
    public void message(ChatMessageRequest request) {
        chatMessageService.sendMessage(request);
    }
}