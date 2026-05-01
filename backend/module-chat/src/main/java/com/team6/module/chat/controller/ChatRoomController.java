package com.team6.module.chat.controller;

import com.team6.module.chat.dto.request.ChatRoomCreateRequest;
import com.team6.module.chat.dto.response.ChatRoomCreateResponse;
import com.team6.module.chat.dto.response.ChatRoomListResponse;
import com.team6.module.chat.dto.response.LeaveChatRoomResponse;
import com.team6.module.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    //채팅방 생성
    @PostMapping
    public ChatRoomCreateResponse createRoom(
            @RequestBody ChatRoomCreateRequest request,
            Authentication authentication
    ) {
        return chatRoomService.createRoom(request, authentication.getName());
    }

    //채팅방 리스트 가져오기
    @GetMapping
    public ChatRoomListResponse getRooms(Authentication authentication) {
        return chatRoomService.findMyRooms(authentication.getName());
    }

    //채팅방 나가기
    @DeleteMapping("/{roomId}/leave")
    public LeaveChatRoomResponse leaveRoom(
            @PathVariable String roomId,
            Authentication authentication
    ) {
        return chatRoomService.leaveRoom(roomId, authentication.getName());
    }
}