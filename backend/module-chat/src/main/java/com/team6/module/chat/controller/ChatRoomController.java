package com.team6.module.chat.controller;


import com.team6.module.chat.dto.chatRoom.ChatRoomCreateRequest;
import com.team6.module.chat.dto.chatRoom.ChatRoomResponse;
import com.team6.module.chat.dto.chatRoom.ChatRoomsResponse;
import com.team6.module.chat.dto.chatRoom.LeaveChatRoomRequest;
import com.team6.module.chat.dto.chatRoom.LeaveChatRoomResponse;
import com.team6.module.chat.service.ChatRoomService;
import com.team6.module.common.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
     * 참여자 퇴장. 요청 본문에 roomId를 담는다.
     * 마지막 참가자가 나가면 MySQL 방·참가자 및 Mongo 메시지를 삭제하고, Redis 알림으로 클라이언트 목록을 갱신한다.
     */
    @PostMapping("/leave")
    public ResponseEntity<LeaveChatRoomResponse> leaveRoom(@Valid @RequestBody LeaveChatRoomRequest request) {
        String userEmail = SecurityUtil.getCurrentUserEmail();
        boolean roomDeleted = chatRoomService.leaveChatRoomAsParticipant(request.roomId(), userEmail);
        return ResponseEntity.ok(LeaveChatRoomResponse.ok(roomDeleted));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<LeaveChatRoomResponse> handleLeaveRoomConflict(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(ex.getStatusCode()).body(LeaveChatRoomResponse.fail(message));
    }

}
