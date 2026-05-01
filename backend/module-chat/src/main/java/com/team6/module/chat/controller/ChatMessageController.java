package com.team6.module.chat.controller;

import com.team6.module.chat.dto.response.ChatMessagePageResponse;
import com.team6.module.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/rooms/{roomId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @GetMapping
    public ChatMessagePageResponse getMessages(
            @PathVariable String roomId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return chatMessageService.getMessages(
                roomId,
                authentication.getName(),
                cursor,
                size
        );
    }
}