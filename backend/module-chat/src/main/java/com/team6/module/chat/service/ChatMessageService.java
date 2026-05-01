package com.team6.module.chat.service;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.repository.MemberRepository;
import com.team6.module.chat.dto.request.ChatMessageSendRequest;
import com.team6.module.chat.dto.response.ChatMessagePageResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    //지난 채팅 메시지 가져오기
    @Transactional(readOnly = true)
    public ChatMessagePageResponse getMessages(
            String roomId,
            String userEmail,
            LocalDateTime cursor,
            int size
    ) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomIdWithParticipants(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        boolean joined = chatRoom.getParticipants().stream()
                .anyMatch(participant -> participant.getUserEmail().equals(userEmail));

        if (!joined) {
            throw new ParticipantNotFoundException();
        }

        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(0, safeSize);

        LocalDateTime effectiveCursor = cursor == null
                ? LocalDateTime.now().plusYears(100)
                : cursor;

        Slice<ChatMessage> slice =
                chatMessageRepository.findByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                        roomId,
                        effectiveCursor,
                        pageable
                );

        List<ChatMessageResponse> messages = slice.getContent().stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getRoomId(),
                        message.getSenderEmail(),
                        message.getSenderNickname(),
                        message.getMessage(),
                        message.getCreatedAt()
                ))
                .toList();

        LocalDateTime nextCursor = messages.isEmpty()
                ? null
                : messages.get(messages.size() - 1).createdAt();

        return new ChatMessagePageResponse(
                messages,
                nextCursor,
                slice.hasNext()
        );
    }

}
