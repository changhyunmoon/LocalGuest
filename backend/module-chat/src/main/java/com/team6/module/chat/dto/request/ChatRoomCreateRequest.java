package com.team6.module.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 채팅방 생성 요청 DTO
 */
public record ChatRoomCreateRequest(
        @NotBlank(message = "채팅방 이름은 필수입니다.")
        String title,

        @NotEmpty(message = "최소 한 명 이상의 참여자를 선택해야 합니다.")
        List<String> participantEmails // Long IDs에서 String Emails로 변경
) {
}