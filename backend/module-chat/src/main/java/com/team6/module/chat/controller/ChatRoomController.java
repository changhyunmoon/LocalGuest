package com.team6.module.chat.controller;

import com.team6.module.chat.dto.request.CreateChatRoomRequest;
import com.team6.module.chat.dto.response.ChatRoomResponse;
import com.team6.module.chat.dto.response.CreateChatRoomResponse;
import com.team6.module.chat.dto.response.ChatRoomListResponse;
import com.team6.module.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<CreateChatRoomResponse> createChatRoom(
            @RequestBody CreateChatRoomRequest request) {

        CreateChatRoomResponse response = chatRoomService.createChatRoom(
                request.creatorId(),
                request
        );

        return ResponseEntity.status(200).body(response);
    }

    //채팅방 리스트 조회
    @GetMapping
    public ResponseEntity<List<ChatRoomListResponse>> getChatRooms(@RequestParam Long userId) {
        // 나중에는 @AuthenticationPrincipal로 userId 획득
        List<ChatRoomListResponse> response = chatRoomService.getMyChatRoomList(userId);
        return ResponseEntity.ok(response);
    }

    //채팅방 입장
    @GetMapping("/{roomId}/enter")
    public ResponseEntity<ChatRoomResponse> enterRoom(
            @PathVariable String roomId,
            @RequestParam Long userId) {

        return ResponseEntity.ok(chatRoomService.enterRoom(roomId, userId));
    }

}