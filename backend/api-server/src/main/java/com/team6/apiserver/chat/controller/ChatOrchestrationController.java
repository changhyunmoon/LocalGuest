package com.team6.apiserver.chat.controller;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.chat.dto.chatRoom.ChatRoomCreateRequest;
import com.team6.module.chat.dto.chatRoom.ChatRoomResponse;
import com.team6.module.chat.dto.chatRoom.ChatRoomsResponse;
import com.team6.module.chat.service.ChatRoomService;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 채팅 모듈은 채팅만 담당하고, 도메인 식별자(guideId 등) → 채팅 생성 입력값(email 등) 해석은 api-server에서 오케스트레이션한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/matching/chat/rooms")
public class ChatOrchestrationController {

    private final ChatRoomService chatRoomService;
    private final GuideProfileRepository guideProfileRepository;
    private final MemberRepository memberRepository;

    /**
     * 게스트가 특정 가이드(guide_profiles PK)와 1:1 DM 방을 조회/생성한다.
     * title 은 비식별 키로 고정한다.
     */
    @PostMapping("/for-guide/{guideId}")
    public ResponseEntity<ChatRoomResponse> getOrCreateDmRoomForGuide(@PathVariable Long guideId) {
        if (!"ROLE_GUEST".equals(SecurityUtil.getCurrentUserRoleString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "게스트만 채팅방을 생성할 수 있습니다.");
        }
        String ownerEmail = SecurityUtil.getCurrentUserEmail();
        String guideEmail = resolveGuideEmail(guideId);
        if (ownerEmail.equalsIgnoreCase(guideEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인과의 채팅방은 만들 수 없습니다.");
        }

        String title = dmTitleForGuide(guideId);

        // 이미 해당 title 방이 있으면 재사용 (내가 참여 중인 방 목록 기준)
        ChatRoomsResponse rooms = chatRoomService.getChatRoomList(ownerEmail);
        ChatRoomResponse existing = rooms.rooms().stream()
                .filter(r -> title.equals(r.title()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return ResponseEntity.ok(existing);
        }

        ChatRoomCreateRequest req = new ChatRoomCreateRequest(title, List.of(guideEmail));
        ChatRoomResponse created = chatRoomService.createChatRoom(ownerEmail, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private String resolveGuideEmail(Long guideId) {
        Long memberId = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가이드를 찾을 수 없습니다."))
                .getMemberId();
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "가이드 계정을 찾을 수 없습니다."))
                .getEmail();
    }

    private static String dmTitleForGuide(Long guideId) {
        return "LG-DM-GUIDE-" + guideId;
    }
}

