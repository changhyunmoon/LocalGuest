package com.team6.module.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ChatRoomCreateRequest {
    @NotBlank(message = "채팅방 이름은 필수입니다.")
    private String title;

    @NotEmpty(message = "최소 한 명 이상의 참여자를 선택해야 합니다.")
    private List<Long> participantUserIds; // 초대된 유저 ID 리스트
}