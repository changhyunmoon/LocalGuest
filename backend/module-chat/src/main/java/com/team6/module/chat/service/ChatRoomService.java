package com.team6.module.chat.service;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.chat.dto.chatRoom.ChatRoomCreateRequest;
import com.team6.module.chat.dto.chatRoom.ChatRoomResponse;
import com.team6.module.chat.dto.chatRoom.ChatRoomsResponse;
import com.team6.module.chat.dto.notification.ChatNotificationResponse;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import com.team6.module.common.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationService notificationService;
    private final GuideProfileRepository guideProfileRepository;
    private final MemberRepository memberRepository;

    //채팅방 생성
    @Transactional
    public ChatRoomResponse createChatRoom(String ownerEmail, ChatRoomCreateRequest request) {
        // 1. 방 엔티티 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .title(request.title())
                .ownerEmail(ownerEmail)
                .build();

        // 2. 방장 추가 (User_ 등의 접두어는 닉네임 정책에 따라 수정하세요)
        ChatParticipant owner = ChatParticipant.create(ownerEmail, "User_" + ownerEmail.split("@")[0]);
        chatRoom.addParticipant(owner);

        // 3. 초대받은 참여자들 추가
        request.participantEmails().forEach(email -> {
            ChatParticipant participant = ChatParticipant.create(email, "User_" + email.split("@")[0]);
            chatRoom.addParticipant(participant);
        });

        // 4. 저장 (CascadeType.ALL 설정 덕분에 참여자도 함께 저장됨)
        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);
        ChatRoomResponse response = ChatRoomResponse.from(savedRoom);

        request.participantEmails().forEach(email -> {
            notificationService.broadcast(ChatNotificationResponse.of(
                    "NEW_ROOM", savedRoom.getRoomId(), ownerEmail, email, response
            ));
        });

        return response;
    }

    /**
     * 게스트가 특정 가이드(guide_profiles PK)와 1:1 DM 방을 조회/생성한다.
     * title 은 가이드 기준 비식별 키로 고정하고(ownerEmail과 조합), 게스트별 중복 생성을 막는다.
     */
    @Transactional
    public ChatRoomResponse getOrCreateDmRoomForGuide(Long guideId) {
        String role = SecurityUtil.getCurrentUserRoleString();
        if (!"ROLE_GUEST".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "게스트만 채팅방을 생성할 수 있습니다.");
        }

        String ownerEmail = SecurityUtil.getCurrentUserEmail();
        String guideEmail = resolveGuideEmail(guideId);
        if (ownerEmail != null && ownerEmail.equalsIgnoreCase(guideEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인과의 채팅방은 만들 수 없습니다.");
        }

        String title = dmTitleForGuide(guideId);
        Optional<ChatRoom> existing = chatRoomRepository.findByTitleAndOwnerEmailWithParticipants(title, ownerEmail);
        if (existing.isPresent()) {
            ChatRoom room = existing.get();
            if (containsEmail(room, guideEmail)) {
                return ChatRoomResponse.from(room);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "기존 채팅방 참여자 정보가 올바르지 않습니다.");
        }

        ChatRoomCreateRequest req = new ChatRoomCreateRequest(title, List.of(guideEmail));
        return createChatRoom(ownerEmail, req);
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

    private static boolean containsEmail(ChatRoom room, String email) {
        if (email == null) return false;
        String target = email.toLowerCase(Locale.ROOT);
        return room.getParticipants().stream()
                .map(ChatParticipant::getUserEmail)
                .filter(e -> e != null)
                .map(e -> e.toLowerCase(Locale.ROOT))
                .anyMatch(target::equals);
    }

    //참여 중인 채팅방 목록 조회
    @Transactional(readOnly = true)
    public ChatRoomsResponse getChatRoomList(String userEmail) {
        // 1. 내가 참여 중인 모든 방 목록 조회 (MySQL)
        List<ChatRoom> userRooms = chatRoomRepository.findAllByUserEmail(userEmail);

        List<ChatRoomResponse> responses = userRooms.stream()
                .map(room -> {
                    // 2. 해당 방에서 '나'의 참여 정보(lastReadAt) 추출
                    ChatParticipant me = room.getParticipants().stream()
                            .filter(p -> p.getUserEmail().equals(userEmail))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("참여자 정보를 찾을 수 없습니다."));

                    // 3. 시간 기반 계산: MongoDB에서 내 lastReadAt보다 늦게 생성된 메시지 카운트
                    // 만약 한 번도 안 읽었다면 lastReadAt이 방 생성 시간일 것이므로 정확히 계산됨
                    long unreadCount = chatMessageRepository.countByRoomIdAndCreatedAtAfter(
                            room.getRoomId(),
                            me.getLastReadAt()
                    );

                    return ChatRoomResponse.of(room, unreadCount);
                })
                // 4. 정렬: 안 읽은 메시지가 있는 방 우선 -> 최신 메시지 시간 순
                .sorted(Comparator.comparing((ChatRoomResponse res) -> res.unreadCount() > 0).reversed()
                        .thenComparing(ChatRoomResponse::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return ChatRoomsResponse.of(responses);
    }

    @Transactional
    public void updateLastReadAt(String roomId, String userEmail) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));

        chatRoom.getParticipants().stream()
                .filter(p -> p.getUserEmail().equals(userEmail))
                .findFirst()
                .ifPresent(ChatParticipant::updateLastReadAt);

    }

}
