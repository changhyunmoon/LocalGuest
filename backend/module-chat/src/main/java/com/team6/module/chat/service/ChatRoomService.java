package com.team6.module.chat.service;


import com.team6.module.chat.dto.request.ChatRoomCreateRequest;
import com.team6.module.chat.dto.response.*;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.exception.ChatExceptionCode;
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
    private static final int PAGE_SIZE = 20;// 한 페이지당 20개씩 로드


    //채팅방 생성
    @Transactional
    public ChatRoomResponse createChatRoom(Long ownerId, ChatRoomCreateRequest request){

        //채팅방 엔티티 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .title(request.getTitle())
                .ownerId(ownerId)
                .build();

        //여기서 participant 정보들은 member 구현되면 조회해서 정보들 가져와야 된다.
        //방장 참여자 추가
        ChatParticipant owner = ChatParticipant.create(ownerId, "User_" + ownerId);
        chatRoom.addParticipant(owner);
        //참여자 리스트 생성
        request.getParticipantUserIds().forEach(userId -> {
            ChatParticipant participant = ChatParticipant.create(userId, "User_" + userId); // 임시 닉네임
            chatRoom.getParticipants().add(participant);
        });

        //참여 인원수 업데이트
        chatRoom.updateParticipantCount();

        //mysql에 저장
        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);

        return ChatRoomResponse.from(savedRoom);
    }

    //채팅방 목록 조회
    @Transactional(readOnly = true)
    public ChatRoomsResponse getChatRoomList(Long userId){

        //참여중인 방 목록 조회
        List<ChatRoom> userRooms = chatRoomRepository.findAllByUserId(userId);

        List<ChatRoomListResponse> roomResponses = userRooms.stream()
                .map(room -> {
                    // 해당 방에서 나의 참여 정보(lastReadAt) 가져오기
                    ChatParticipant me = room.getParticipants().stream()
                            .filter(p -> p.getUserId().equals(userId))
                            .findFirst()
                            .orElseThrow(ParticipantNotFoundException::new);

                    // MongoDB에서 안 읽은 메시지 수 카운트
                    long unreadCount = chatMessageRepository.countByRoomIdAndCreatedAtAfter(
                            room.getRoomId(),
                            me.getLastReadAt()
                    );

                    return ChatRoomListResponse.builder()
                            .roomId(room.getRoomId())
                            .title(room.getTitle())
                            .participantCount(room.getParticipantCount())
                            .lastMessage(room.getLastMessage())
                            .lastMessageAt(room.getLastMessageAt())
                            .unreadCount(unreadCount)
                            .build();
                })
                // 정렬 로직: 안 읽은 메시지가 있는 방 우선 -> 최신 메시지 수신순
                .sorted(Comparator.comparing((ChatRoomListResponse res) -> res.getUnreadCount() > 0).reversed()
                        .thenComparing(ChatRoomListResponse::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return ChatRoomsResponse.of(roomResponses);
    }

    //채팅방 메시지 로딩
    public ChatMessagePageResponse getMessageLog(String roomId, String cursor) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE + 1); // 다음 페이지 여부 확인을 위해 +1개 조회

        List<ChatMessage> entities;
        if (cursor == null || cursor.isBlank()) {
            // 처음 입장 시 최신 메시지 조회
            entities = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
        } else {
            // 커서(마지막 메시지 ID) 기준 이전 메시지 조회
            entities = chatMessageRepository.findByRoomIdAndIdLessThanOrderByCreatedAtDesc(roomId, cursor, pageable);
        }

        boolean hasNext = entities.size() > PAGE_SIZE;
        List<ChatMessageResponse> responses = entities.stream()
                .limit(PAGE_SIZE) // 실제 사이즈만큼 제한
                .map(ChatMessageResponse::from)
                .toList();

        return ChatMessagePageResponse.of(responses, hasNext);
    }

    @Transactional
    public void updateLastMessage(String roomId, String message, LocalDateTime sentAt) {
        chatRoomRepository.findByRoomId(roomId)
                .ifPresent(room -> {
                    room.updateLastMessage(message, sentAt);
                    // Transactional 안에서 엔티티를 수정하므로 더티 체킹에 의해 자동 반영됩니다.
                });
    }

    //채팅방 단순 조회
    @Transactional(readOnly = true)
    public ChatRoom getRoomEntity(String roomId) {
        return chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);
    }
}
