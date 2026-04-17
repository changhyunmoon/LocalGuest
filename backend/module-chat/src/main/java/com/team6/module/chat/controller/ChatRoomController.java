package com.team6.module.chat.controller;


import com.team6.module.chat.dto.chatRoom.ChatRoomCreateRequest;
import com.team6.module.chat.dto.chatRoom.ChatRoomResponse;
import com.team6.module.chat.dto.chatRoom.ChatRoomsResponse;
import com.team6.module.chat.service.ChatRoomService;
import com.team6.module.common.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    //채팅방 생성
    @PostMapping
    public ResponseEntity<ChatRoomResponse> createRoom(
            @Valid @RequestBody ChatRoomCreateRequest request
    ) {
        String ownerEmail = SecurityUtil.getCurrentUserEmail();

        ChatRoomResponse response = chatRoomService.createChatRoom(ownerEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //참여 중인 채팅방 목록 조회
    @GetMapping
    public ResponseEntity<ChatRoomsResponse> getRoomList() {
        String userEmail = SecurityUtil.getCurrentUserEmail();

        ChatRoomsResponse response = chatRoomService.getChatRoomList(userEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * 매칭 요청 단위 채팅방 조회 또는 생성 (게스트/해당 가이드만).
     * 프론트는 응답의 roomId로 /messages?roomId= 로 이동하면 된다.
     */
    @PostMapping("/for-match-request/{requestId}")
    public ResponseEntity<ChatRoomResponse> getOrCreateForMatchRequest(@PathVariable Long requestId) {
        ChatRoomResponse response = chatRoomService.getOrCreateRoomForMatchRequest(requestId);
        return ResponseEntity.ok(response);
    }

}
