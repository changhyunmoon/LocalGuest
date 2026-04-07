package com.team6.module.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateChatRoomRequest(

        @NotNull(message = "생성자 ID는 필수입니다.")
        Long creatorId,

        @NotBlank(message = "채팅방 이름을 입력해주세요.")
        @Size(max = 50, message = "채팅방 이름은 50자 이하로 입력해주세요.")
        String title,

        @NotEmpty(message = "초대할 멤버를 1명 이상 선택해주세요.")
        @Size(max = 99, message = "채팅방 인원은 최대 100명까지 가능합니다.")
        List<Long> invitedMemberIds

) {}