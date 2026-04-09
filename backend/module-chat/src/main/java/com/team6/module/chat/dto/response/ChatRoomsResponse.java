package com.team6.module.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class ChatRoomsResponse {
    private int totalCount; // 참여 중인 총 채팅방 수
    private List<ChatRoomListResponse> rooms; // 채팅방 상세 정보 리스트

    public static ChatRoomsResponse of(List<ChatRoomListResponse> rooms) {
        return new ChatRoomsResponse(rooms.size(), rooms);
    }
}