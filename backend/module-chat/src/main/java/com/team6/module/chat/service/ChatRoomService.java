package com.team6.module.chat.service;

import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.domain.matching.repository.MatchRequestRepository;
import com.team6.domain.matching.support.MatchingAuthenticationSupport;
import com.team6.domain.member.entity.Role;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationService notificationService;
    private final MatchRequestRepository matchRequestRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final MemberRepository memberRepository;
    private final MatchingAuthenticationSupport matchingAuthenticationSupport;

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
     * 매칭 요청에 묶인 1:1 채팅방을 조회하거나 없으면 생성한다.
     * 제목은 {@code LG-MATCH-{requestId}} 로 고정해 동일 매칭에 대한 중복 방 생성을 막는다.
     */
    @Transactional
    public ChatRoomResponse getOrCreateRoomForMatchRequest(Long requestId) {
        MatchRequest mr = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND));

        assertMatchAllowsChat(mr);
        assertUserPartyToMatch(mr);

        String guestEmail = memberRepository.findById(mr.getGuestId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND))
                .getEmail();
        Long guideMemberId = guideProfileRepository.findById(mr.getGuideId())
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND))
                .getMemberId();
        String guideEmail = memberRepository.findById(guideMemberId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCH_REQUEST_NOT_FOUND))
                .getEmail();

        if (guestEmail.equalsIgnoreCase(guideEmail)) {
            throw new MatchingException(MatchingErrorCode.GUEST_GUIDE_SAME);
        }

        String title = matchRoomTitle(requestId);
        Optional<ChatRoom> existing = chatRoomRepository.findFirstByTitleOrderByIdAsc(title);
        if (existing.isPresent()) {
            ChatRoom room = existing.get();
            if (roomContainsBoth(room, guestEmail, guideEmail)) {
                log.info("[MATCH_CHAT] 기존 채팅방 사용 — requestId={}, roomId={}", requestId, room.getRoomId());
                return ChatRoomResponse.from(room);
            }
            log.warn("[MATCH_CHAT] 제목 충돌(참여자 불일치) — requestId={}, roomId={}", requestId, room.getRoomId());
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        String ownerEmail = SecurityUtil.getCurrentUserEmail();
        String counterpart = ownerEmail.equalsIgnoreCase(guestEmail) ? guideEmail : guestEmail;

        ChatRoomCreateRequest req = new ChatRoomCreateRequest(
                title,
                List.of(counterpart)
        );
        ChatRoomResponse created = createChatRoom(ownerEmail, req);
        log.info("[MATCH_CHAT] 채팅방 생성 — requestId={}, roomId={}", requestId, created.roomId());
        return created;
    }

    private static String matchRoomTitle(long requestId) {
        return "LG-MATCH-" + requestId;
    }

    private static boolean roomContainsBoth(ChatRoom room, String guestEmail, String guideEmail) {
        String g = guestEmail.toLowerCase(Locale.ROOT);
        String gv = guideEmail.toLowerCase(Locale.ROOT);
        return room.getParticipants().stream().map(ChatParticipant::getUserEmail)
                .map(e -> e.toLowerCase(Locale.ROOT))
                .filter(e -> e.equals(g) || e.equals(gv))
                .distinct()
                .count() >= 2;
    }

    private void assertMatchAllowsChat(MatchRequest mr) {
        MatchRequestStatus s = mr.getStatus();
        boolean ok = switch (s) {
            case ACCEPTED, PAID, IN_PROGRESS, COMPLETED -> true;
            default -> false;
        };
        if (!ok) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
    }

    private void assertUserPartyToMatch(MatchRequest mr) {
        Role r = matchingAuthenticationSupport.resolveTokenRole();
        if (r == Role.GUEST) {
            Long guestMemberId = matchingAuthenticationSupport.getCurrentGuestMemberId();
            if (!mr.getGuestId().equals(guestMemberId)) {
                throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
            }
            return;
        }
        if (r == Role.GUIDE) {
            Long guideProfileId = matchingAuthenticationSupport.getCurrentGuideProfileId();
            if (!mr.getGuideId().equals(guideProfileId)) {
                throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
            }
            return;
        }
        throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_UNAUTHORIZED);
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
