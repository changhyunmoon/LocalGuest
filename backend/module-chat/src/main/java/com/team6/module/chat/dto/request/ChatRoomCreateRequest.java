package com.team6.module.chat.dto.request;

import java.util.List;

public record ChatRoomCreateRequest(
        String title,
        Long ownerId,
        String ownerNickname,
        List<ParticipantInfo> participants // 초대할 인원 목록
) {
    public record ParticipantInfo(
            Long userId,
            String nickname
    ) {}
}