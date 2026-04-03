package com.team6.module.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {
    private final ObjectMapper objectMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String publishMessage = new String(message.getBody());
            System.out.println("<<< Redis Raw Message: " + publishMessage);

            ChatMessageResponse chatMessage = objectMapper.readValue(publishMessage, ChatMessageResponse.class);

            System.out.println(">>> roomId: " + chatMessage.roomId());

            String destination = "/sub/chat/room/" + chatMessage.roomId();
            messagingTemplate.convertAndSend(destination, chatMessage);

            System.out.println(">>> WebSocket 쐈음! 경로: " + destination);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}