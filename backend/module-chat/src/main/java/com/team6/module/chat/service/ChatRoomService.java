package com.team6.module.chat.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.chat.dto.request.ChatRoomCreateRequest;
import com.team6.module.chat.dto.response.ChatRoomCreateResponse;
import com.team6.module.chat.dto.response.ChatRoomListResponse;
import com.team6.module.chat.dto.response.LeaveChatRoomResponse;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.exception.ChatRoomNotFoundException;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;

    //채팅방 생성
    @Transactional
    public ChatRoomCreateResponse createRoom(
            ChatRoomCreateRequest request,
            String ownerEmail
    ) {
        Member owner = memberRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("방장 회원을 찾을 수 없습니다."));

        Set<String> participantEmails = new LinkedHashSet<>();
        participantEmails.add(ownerEmail);

        if (request.participantEmails() != null) {
            participantEmails.addAll(request.participantEmails());
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .title(request.title())
                .ownerEmail(ownerEmail)
                .build();

        for (String email : participantEmails) {
            Member member = memberRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("참여자 회원을 찾을 수 없습니다: " + email));

            ChatParticipant participant = ChatParticipant.builder()
                    .userEmail(member.getEmail())
                    .userNickname(member.getNickname())
                    .build();

            chatRoom.addParticipant(participant);
        }

        ChatRoom saved = chatRoomRepository.save(chatRoom);

        List<ChatRoomCreateResponse.ParticipantResponse> participants =
                saved.getParticipants().stream()
                        .map(participant -> new ChatRoomCreateResponse.ParticipantResponse(
                                participant.getUserEmail(),
                                participant.getUserNickname()
                        ))
                        .toList();

        return new ChatRoomCreateResponse(
                saved.getRoomId(),
                saved.getTitle(),
                participants
        );
    }

    //채팅방 리스트 가져오기
    @Transactional(readOnly = true)
    public ChatRoomListResponse findMyRooms(String userEmail) {
        List<ChatRoom> rooms = chatRoomRepository.findAllByUserEmail(userEmail);

        List<ChatRoomListResponse.ChatRoomSummaryResponse> responses =
                rooms.stream()
                        .map(room -> new ChatRoomListResponse.ChatRoomSummaryResponse(
                                room.getRoomId(),
                                room.getTitle(),
                                room.getLastMessage(),
                                room.getLastMessageAt(),
                                room.getParticipantCount()
                        ))
                        .toList();

        return new ChatRoomListResponse(responses);
    }

    //채팅방 나가기
    @Transactional
    public LeaveChatRoomResponse leaveRoom(String roomId, String userEmail) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomIdWithParticipants(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        chatRoom.removeParticipant(userEmail);

        boolean roomDeleted = false;

        if (chatRoom.isEmpty()) {
            chatMessageRepository.deleteByRoomId(roomId);
            chatRoomRepository.delete(chatRoom);
            roomDeleted = true;
        }

        return new LeaveChatRoomResponse(
                roomId,
                userEmail,
                true,
                roomDeleted
        );
    }

}
