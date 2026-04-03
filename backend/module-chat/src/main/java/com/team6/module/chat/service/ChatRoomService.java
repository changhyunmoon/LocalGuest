package com.team6.module.chat.service;

import com.team6.module.chat.dto.request.ChatRoomCreateRequest;
import com.team6.module.chat.dto.response.ChatRoomListResponse;
import com.team6.module.chat.dto.response.ChatRoomResponse;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.beans.Transient;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public ChatRoomResponse createChatRoom(ChatRoomCreateRequest request) {
        // 채팅방 엔티티 생성
        ChatRoom chatRoom = ChatRoom.create(request.title(), request.ownerId());

        // 방장 추가
        ChatParticipant owner = ChatParticipant.create(request.ownerId(), request.ownerNickname());
        chatRoom.addParticipant(owner);

        // 초대된 참가자들 추가 (null 체크 및 반복문)
        if (request.participants() != null) {
            request.participants().stream()
                    .map(p -> ChatParticipant.create(p.userId(), p.nickname()))
                    .forEach(chatRoom::addParticipant);
        }

        // 저장 (Cascade로 인해 모든 참여자가 한 번에 저장됨)
        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);

        return ChatRoomResponse.from(savedRoom);
    }

    @Transactional
    public List<ChatRoomListResponse> getMyChatRooms(Long userId) {
        List<ChatRoom> myRooms = chatRoomRepository.findAllByUserId(userId);

        return myRooms.stream()
                .map(ChatRoomListResponse::from)
                .toList();
    }

}
