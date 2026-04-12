package com.team6.module.chat.dto.response;

import lombok.Builder;
import java.util.List;

@Builder
public record ChatRoomsResponse(
        int totalCount,                    // 참여 중인 총 채팅방 수
        List<ChatRoomListResponse> rooms   // 채팅방 상세 정보 리스트
) {
    public static ChatRoomsResponse of(List<ChatRoomListResponse> rooms) {
        return ChatRoomsResponse.builder()
                .totalCount(rooms != null ? rooms.size() : 0)
                .rooms(rooms)
                .build();
    }
}