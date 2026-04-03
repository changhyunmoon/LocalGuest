package com.team6.module.chat.controller;

import com.team6.module.chat.dto.request.ChatRoomCreateRequest;
import com.team6.module.chat.dto.response.ChatRoomListResponse;
import com.team6.module.chat.dto.response.ChatRoomResponse;
import com.team6.module.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;


    @PostMapping
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            @RequestBody ChatRoomCreateRequest request
    ) {
        ChatRoomResponse response = chatRoomService.createChatRoom(request);


        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomListResponse>> getMyRooms(
            @RequestParam("userId") Long userId
    ) {
        List<ChatRoomListResponse> responses = chatRoomService.getMyChatRooms(userId);
        return ResponseEntity.status(200).body(responses);
    }
}