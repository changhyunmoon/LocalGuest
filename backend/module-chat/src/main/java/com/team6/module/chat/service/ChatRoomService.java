package com.team6.module.chat.service;

import com.team6.module.chat.config.RedisPublisher;
import com.team6.module.chat.dto.request.CreateChatRoomRequest;
import com.team6.module.chat.dto.response.ChatRoomListResponse;
import com.team6.module.chat.dto.response.ChatRoomNotification;
import com.team6.module.chat.dto.response.ChatRoomResponse;
import com.team6.module.chat.dto.response.CreateChatRoomResponse;
import com.team6.module.chat.entity.MemberValidator;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.exception.ChatParticipantNotFoundException;
import com.team6.module.chat.exception.ChatRoomNotFoundException;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageService chatMessageService;
    private final MemberValidator memberValidator;
    private final RedisPublisher redisPublisher;

    //채팅방 생성
    public CreateChatRoomResponse createChatRoom(Long creatorId, CreateChatRoomRequest request){

        List<Long> allMemberIds = new ArrayList<>(request.invitedMemberIds());
        if (!allMemberIds.contains(creatorId)) {
            allMemberIds.add(creatorId);
        }
        //검증 + 닉네임 조회
        Map<Long, String> nicknameMap = memberValidator.getValidatedNicknameMap(allMemberIds);

        ChatRoom chatRoom = ChatRoom.create(request.title(), creatorId);

        allMemberIds.forEach(memberId ->
                chatRoom.addParticipant(
                        ChatParticipant.create(memberId, nicknameMap.get(memberId))
                )
        );

        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);

        ChatRoomNotification notification = ChatRoomNotification.from(savedRoom);
        request.invitedMemberIds().stream()
                .filter(id -> !id.equals(creatorId))
                .forEach(id -> redisPublisher.publishNotification(id, notification));

        return CreateChatRoomResponse.from(savedRoom);

    }

    //채팅방 목록 조회
    @Transactional(readOnly = true)
    public List<ChatRoomListResponse> getMyChatRoomList(Long userId) {
        // 1. 내가 참여 중인 모든 채팅방 조회 (참여자 정보 fetch join)
        List<ChatRoom> rooms = chatRoomRepository.findAllWithParticipantsByUserId(userId);

        return rooms.stream()
                .map(room -> {
                    // 2. 내 참여 정보(lastReadAt) 추출
                    ChatParticipant me = room.getParticipants().stream()
                            .filter(p -> p.getUserId().equals(userId))
                            .findFirst()
                            .orElseThrow(ChatParticipantNotFoundException::new);

                    // 3. 내 lastReadAt 이후의 메시지 개수 카운트
                    Long unreadCount = chatMessageService.countUnreadMessages(room.getRoomId(), me.getLastReadAt());

                    return new ChatRoomListResponse(
                            room.getRoomId(),
                            room.getTitle(),
                            room.getParticipantCount(),
                            unreadCount,
                            room.getLastMessage(),
                            room.getLastMessageAt()
                    );
                })
                // 4.  정렬 수행
                .sorted((o1, o2) -> {
                    // 정렬 기준 1: 안 읽은 메시지가 있는 방(unreadCount > 0)을 위로
                    boolean o1HasUnread = o1.unreadCount() > 0;
                    boolean o2HasUnread = o2.unreadCount() > 0;

                    if (o1HasUnread != o2HasUnread) {
                        return o1HasUnread ? -1 : 1; // 안 읽은 쪽이 앞으로(-1)
                    }

                    // 정렬 기준 2: 둘 다 안 읽었거나 둘 다 읽었다면, 최신 메시지 시간순(내림차순)
                    if (o1.lastMessageAt() == null) return 1;
                    if (o2.lastMessageAt() == null) return -1;
                    return o2.lastMessageAt().compareTo(o1.lastMessageAt());
                })
                .toList();
    }

    //채팅방 목록 재정렬
    @Transactional
    public void updateLastMessage(String roomId, String lastMessage, LocalDateTime lastMessageAt) {
        ChatRoom room = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        // ChatRoom 엔티티에 업데이트 메서드를 추가해두세요.
        room.updateLastMessage(lastMessage, lastMessageAt);
    }

    //채팅방 입장
    @Transactional
    public ChatRoomResponse enterRoom(String roomId, Long userId){
        //방 존재 여부 확인
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        //읽음 시각 최산화, 안 읽은 메시지 0개 처리
        chatRoom.getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .ifPresent(ChatParticipant::updateLastRead);

        return ChatRoomResponse.from(chatRoom);
    }


}
