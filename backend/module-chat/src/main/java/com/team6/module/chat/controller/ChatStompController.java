package com.team6.module.chat.controller;

import com.team6.module.chat.service.RedisPublisher;
import com.team6.module.chat.dto.request.ChatMessageRequest;
import com.team6.module.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;


@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final RedisPublisher redisPublisher;
    private final ChatMessageService chatMessageService;

    // 클라이언트가 /pub/chat/message 로 메시지를 보내면 호출됨
    @MessageMapping("/chat/message")
    public void message(ChatMessageRequest message) {
        // 1. DB(MongoDB) 저장
        chatMessageService.saveMessage(message);

        // 2. Redis Topic으로 메시지 발행 (이후 Subscriber가 받아 웹소켓으로 전송)
        redisPublisher.publish(new ChannelTopic("chat"), message);
    }
}