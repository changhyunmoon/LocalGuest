package com.team6.module.chat.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.chat.dto.request.ChatMessageSendRequest;
import com.team6.module.chat.dto.response.ChatMessageResponse;
import com.team6.module.chat.entity.mongodb.ChatMessage;
import com.team6.module.chat.entity.mongodb.MessageType;
import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;
import com.team6.module.chat.exception.ChatRoomNotFoundException;
import com.team6.module.chat.exception.ParticipantNotFoundException;
import com.team6.module.chat.repository.mongodb.ChatMessageRepository;
import com.team6.module.chat.repository.mysql.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;

    public ChatMessageResponse saveAndPublishReadyMessage(
            ChatMessageSendRequest request,
            String senderEmail
    ) {
        //해당 채팅방이 있는지 확인
        ChatRoom chatRoom = chatRoomRepository.findByRoomIdWithParticipants(request.roomId())
                .orElseThrow(ChatRoomNotFoundException::new);
        //해당 채팅방에 보낸 사람이 참여 중인지
        ChatParticipant senderParticipant = chatRoom.getParticipants().stream()
                .filter(participant -> participant.getUserEmail().equals(senderEmail))
                .findFirst()
                .orElseThrow(ParticipantNotFoundException::new);

        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(chatRoom.getRoomId())
                .senderEmail(senderParticipant.getUserEmail())
                .senderNickname(senderParticipant.getUserNickname())
                .message(request.message())
                .type(MessageType.CHAT)
                .build();

        ChatMessage saved = chatMessageRepository.save(chatMessage);

        return new ChatMessageResponse(
                saved.getId(),
                saved.getRoomId(),
                saved.getSenderEmail(),
                saved.getSenderNickname(),
                saved.getMessage(),
                saved.getCreatedAt()
        );
    }

}
