package com.team6.module.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRoomReadAckRequest(
        @NotBlank(message = "채팅방 ID는 필수입니다.")
        String roomId,
        @NotNull(message = "유저 ID는 필수입니다.")
        Long userId,
        @NotNull(message = "마지막으로 확인한 메시지 seq는 필수입니다.")
        Long lastSeenSeq
) {
}

