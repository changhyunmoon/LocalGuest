package com.team6.module.chat.controller;


import com.team6.module.chat.dto.chatMessage.ChatMessageResponse;
import com.team6.module.chat.dto.chatMessage.ChatPagingResponse;
import com.team6.module.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat/rooms")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;


    @GetMapping("/{roomId}/messages")
    public ResponseEntity<ChatPagingResponse<ChatMessageResponse>> getChatMessages(
            @PathVariable String roomId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        // 1. 서비스에서 Slice<ChatMessageResponse>를 가져옴
        Slice<ChatMessageResponse> slice = chatMessageService.getChatMessages(roomId, pageable);

        // 2. 가공된 페이징 응답 객체로 변환하여 반환
        return ResponseEntity.ok(ChatPagingResponse.from(slice));
    }

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable String roomId, @RequestParam String email) {
        chatMessageService.markAsRead(roomId, email);
        return ResponseEntity.ok().build();
    }
}