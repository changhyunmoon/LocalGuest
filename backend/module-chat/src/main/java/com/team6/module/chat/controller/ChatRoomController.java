package com.team6.module.chat.controller;


import com.team6.module.chat.dto.request.ChatRoomCreateRequest;
import com.team6.module.chat.dto.response.ChatRoomResponse;
import com.team6.module.chat.dto.response.ChatRoomsResponse;
import com.team6.module.chat.service.ChatRoomService;
import jakarta.validation.Valid;
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

    //채팅방 생성
    @PostMapping
    public ResponseEntity<ChatRoomResponse> createRoom(
            @Valid @RequestBody ChatRoomCreateRequest request
            // @AuthenticationPrincipal CustomUserDetails userDetails // 나중에 활성화
    ) {
        //Long userId = userDetails.getUserId(); 나중에 spring security 구현되면 활성화
        Long userId = 1L;
        ChatRoomResponse response = chatRoomService.createChatRoom(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //참여중인 채팅방 목록 조회
    @GetMapping
    public ResponseEntity<ChatRoomsResponse> getRoomList(
            //@AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        //Long userId = userDetails.getUserId(); 나중에 spring security 구현되면 활성화
        Long userId = 1L;
        ChatRoomsResponse response = chatRoomService.getChatRoomList(userId);
        return ResponseEntity.ok(response);
    }

}