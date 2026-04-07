package com.team6.module.chat.controller;


import com.team6.module.chat.dto.request.SendMessageRequest;
import com.team6.module.chat.entity.MemberValidator;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.service.ChatMessageService;
import com.team6.module.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final MemberValidator memberValidator;

    @MessageMapping("/chat/message")
    public void sendMessage(SendMessageRequest request) {
        // 1. 발신자 닉네임 조회
        String nickname = memberValidator.getNickname(request.senderId());

        // 2. 메시지 저장 (MongoDB)
        ChatMessage savedMessage = chatMessageService.saveMessage(request, nickname);

        // 3. 채팅방 마지막 메시지 갱신 (MySQL - 리스트 정렬용)
        chatRoomService.updateLastMessage(
                request.roomId(),
                request.message(),
                savedMessage.getCreatedAt()
        );

        // 4. 해당 방 구독자들에게 실시간 브로드캐스팅
        // 구독 경로 예시: /sub/chat/room/{roomId}
        messagingTemplate.convertAndSend("/sub/chat/room/" + request.roomId(), savedMessage);
    }


}