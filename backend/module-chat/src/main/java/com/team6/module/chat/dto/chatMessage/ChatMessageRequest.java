package com.team6.module.chat.dto.chatMessage;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        @NotBlank(message = "채팅방 ID는 필수입니다.")
        String roomId,

        @NotBlank(message = "보내는 사람 이메일은 필수입니다.")
        String senderEmail,

        @NotBlank(message = "보내는 사람 닉네임은 필수입니다.")
        String senderNickname,

        @NotBlank(message = "메시지 내용은 비어있을 수 없습니다.")
        String message
) {
}