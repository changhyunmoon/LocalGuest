package com.team6.module.chat.controller;


import com.team6.module.chat.dto.request.ChatMessageRequest;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import com.team6.module.chat.dto.response.ChatScrollResponse;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService chatMessageService;

    /**
     * [STOMP] 메시지 전송
     */
    @MessageMapping("/chat/message")
    public void sendMessage(@Payload ChatMessageRequest request) {

        ChatMessageResponse response = chatMessageService.processSendMessage(request);

        // 결과 브로드캐스팅
        messagingTemplate.convertAndSend("/sub/chat/room/" + request.roomId(), response);
    }

    /**
     * [HTTP] 이전 대화 내용 조회 (무한 스크롤)
     */
    @GetMapping("/api/chat/rooms/{roomId}/messages")
    public ResponseEntity<ChatScrollResponse> getMessages(
            @PathVariable String roomId,
            @RequestParam(required = false) String lastMessageId) {

        return ResponseEntity.ok(chatMessageService.getMessagesBefore(roomId, lastMessageId));
    }
}