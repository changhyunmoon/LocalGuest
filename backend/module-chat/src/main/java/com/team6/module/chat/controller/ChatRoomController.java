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

    /** 참여자 퇴장. 마지막 참가자가 나가면 MySQL 방·참가자 및 Mongo 메시지를 삭제한다. */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId) {
        String userEmail = SecurityUtil.getCurrentUserEmail();
        chatRoomService.leaveChatRoomAsParticipant(roomId, userEmail);
        return ResponseEntity.noContent().build();
    }

}
