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
     * 1:1 DM 채팅방 조회/생성 (게스트 → 특정 가이드).
     * 가이드 이메일은 서버가 guideId(guide_profiles PK)로 해석한다.
     */
    @PostMapping("/for-guide/{guideId}")
    public ResponseEntity<ChatRoomResponse> getOrCreateForGuide(@PathVariable Long guideId) {
        ChatRoomResponse response = chatRoomService.getOrCreateDmRoomForGuide(guideId);
        return ResponseEntity.ok(response);
    }

}
