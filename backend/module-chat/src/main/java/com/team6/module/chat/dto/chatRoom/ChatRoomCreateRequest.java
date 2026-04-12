package com.team6.module.chat.dto.chatRoom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatRoomCreateRequest(
        @NotBlank(message = "채팅방 이름은 필수입니다.")
        String title,

        @NotEmpty(message = "최소 한 명 이상의 참여자를 선택해야 합니다.")
        List<String> participantEmails
) {}