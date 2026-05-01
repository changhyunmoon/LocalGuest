package com.team6.module.chat.dto.response;

import java.util.List;

public record ChatRoomCreateResponse(
        String roomId,
        String title,
        List<ParticipantResponse> participants
) {
    public record ParticipantResponse(
            String email,
            String nickname
    ) {
    }
}