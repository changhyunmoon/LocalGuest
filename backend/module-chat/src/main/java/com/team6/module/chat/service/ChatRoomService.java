package com.team6.module.chat.service;

import com.team6.module.chat.dto.chatRoom.ChatRoomCreateRequest;
import com.team6.module.chat.dto.chatRoom.ChatRoomResponse;
import com.team6.module.chat.dto.chatRoom.ChatRoomsResponse;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
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

        return ChatRoomResponse.from(savedRoom);
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
