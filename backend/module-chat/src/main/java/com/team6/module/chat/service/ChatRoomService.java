package com.team6.module.chat.service;

import com.team6.module.chat.dto.request.ChatRoomCreateRequest;
import com.team6.module.chat.dto.response.*;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.exception.ChatRoomNotFoundException;
import com.team6.module.chat.exception.ParticipantNotFoundException;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private static final int PAGE_SIZE = 20;

    /**
     * 채팅방 생성
     */
    @Transactional
    public ChatRoomResponse createChatRoom(String ownerEmail, ChatRoomCreateRequest request) {
        // ChatRoom 엔티티 생성 (Record 필드 접근: request.title())
        ChatRoom chatRoom = ChatRoom.builder()
                .title(request.title())
                .ownerEmail(ownerEmail)
                .build();

        // 방장 참여자 추가
        ChatParticipant owner = ChatParticipant.create(ownerEmail, "User_" + ownerEmail.split("@")[0]);
        chatRoom.addParticipant(owner);

        // 초대된 참여자 추가 (Record 필드 접근: request.participantEmails())
        request.participantEmails().forEach(email -> {
            ChatParticipant participant = ChatParticipant.create(email, "User_" + email.split("@")[0]);
            chatRoom.addParticipant(participant);
        });

        chatRoom.updateParticipantCount();

        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);
        return ChatRoomResponse.from(savedRoom);
    }

    /**
     * 참여 중인 채팅방 목록 조회
     */
    @Transactional(readOnly = true)
    public ChatRoomsResponse getChatRoomList(String userEmail) {
        // 참여 중인 방 목록 조회
        List<ChatRoom> userRooms = chatRoomRepository.findAllByUserEmail(userEmail);

        List<ChatRoomListResponse> roomResponses = userRooms.stream()
                .map(room -> {
                    // 내 참여 정보(lastReadAt) 찾기
                    ChatParticipant me = room.getParticipants().stream()
                            .filter(p -> p.getUserEmail().equals(userEmail))
                            .findFirst()
                            .orElseThrow(ParticipantNotFoundException::new);

                    // MongoDB에서 마지막 읽은 시점 이후의 메시지 카운트
                    long unreadCount = chatMessageRepository.countByRoomIdAndCreatedAtAfter(
                            room.getRoomId(),
                            me.getLastReadAt()
                    );

                    // ChatRoomListResponse(Record) 빌더 사용
                    return ChatRoomListResponse.builder()
                            .roomId(room.getRoomId())
                            .title(room.getTitle())
                            .participantCount(room.getParticipantCount())
                            .lastMessage(room.getLastMessage())
                            .lastMessageAt(room.getLastMessageAt())
                            .unreadCount(unreadCount)
                            .build();
                })
                // 정렬: 안 읽은 메시지가 있는 방 우선 -> 최신 메시지 순
                .sorted(Comparator.comparing((ChatRoomListResponse res) -> res.unreadCount() > 0).reversed()
                        .thenComparing(ChatRoomListResponse::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return ChatRoomsResponse.of(roomResponses);
    }

    /**
     * 채팅방 과거 메시지 로딩 (페이징/커서 기반)
     */
    @Transactional(readOnly = true)
    public ChatScrollResponse getMessageLog(String roomId, String cursor) {
        // 1. 다음 페이지 존재 여부 확인을 위해 +1 조회
        Pageable pageable = PageRequest.of(0, PAGE_SIZE + 1);

        List<ChatMessage> entities;

        // 2. 커서(lastMessageId) 존재 여부에 따른 분기 처리
        if (cursor == null || cursor.isBlank()) {
            // 첫 진입 시 최신 메시지 조회
            entities = chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageable);
        } else {
            // 커서 기반 페이징 - String 커서를 ObjectId로 변환하여 쿼리
            // MongoDB Repository에서 _id를 기준으로 Less Than($lt) 비교
            entities = chatMessageRepository.findBeforeId(roomId, new org.bson.types.ObjectId(cursor), pageable);
        }

        // 3. hasNext 판단
        boolean hasNext = entities.size() > PAGE_SIZE;

        // 4. 응답 DTO 변환 (요청한 사이즈만큼 자름)
        List<ChatMessageResponse> responses = entities.stream()
                .limit(PAGE_SIZE)
                .map(ChatMessageResponse::fromEntity)
                .toList();

        return ChatScrollResponse.of(responses, hasNext);
    }

    /**
     * 마지막 메시지 정보 캐시 업데이트
     */
    @Transactional
    public void updateLastMessage(String roomId, String message, LocalDateTime sentAt) {
        chatRoomRepository.findByRoomId(roomId)
                .ifPresent(room -> room.updateLastMessage(message, sentAt));
    }

    /**
     * 엔티티 단순 조회
     */
    @Transactional(readOnly = true)
    public ChatRoom getRoomEntity(String roomId) {
        return chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);
    }

    @Transactional
    public void updateLastReadTime(String roomId, String userEmail) {
        ChatRoom room = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("채팅방을 찾을 수 없습니다."));

        room.getParticipants().stream()
                .filter(p -> p.getUserEmail().equals(userEmail))
                .findFirst()
                .ifPresent(ChatParticipant::updateLastReadAt);

    }

}