package com.team6.module.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatMessageRequest(
        @NotBlank(message = "채팅방 ID는 필수입니다.")
        String roomId,

        @NotNull(message = "보낸 사람 ID는 필수입니다.")
        Long senderId,

        @NotBlank(message = "보낸 사람 닉네임은 필수입니다.")
        String senderNickname,

        @NotBlank(message = "메시지 내용은 비어있을 수 없습니다.")
        String message
) {
}