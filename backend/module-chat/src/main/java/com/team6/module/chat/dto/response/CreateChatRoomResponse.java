package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mysql.ChatParticipant;
import com.team6.module.chat.entity.mysql.ChatRoom;

import java.time.LocalDateTime;
import java.util.List;

public record CreateChatRoomResponse(

        String roomId,
        String title,
        Long ownerId,
        int participantCount,
        List<ParticipantInfo> participants,
        LocalDateTime createdAt

) {

    public static CreateChatRoomResponse from(ChatRoom chatRoom) {
        return new CreateChatRoomResponse(
                chatRoom.getRoomId(),
                chatRoom.getTitle(),
                chatRoom.getOwnerId(),
                chatRoom.getParticipantCount(),
                chatRoom.getParticipants().stream()
                        .map(ParticipantInfo::from)
                        .toList(),
                chatRoom.getCreatedAt()
        );
    }

    public record ParticipantInfo(
            Long memberId,
            String nickname
    ) {

        public static ParticipantInfo from(ChatParticipant participant) {
            return new ParticipantInfo(
                    participant.getUserId(),
                    participant.getUserNickname()
            );
        }
    }
}
