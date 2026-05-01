package com.team6.module.chat.dto.chatRoom;

import java.util.List;

public record ChatRoomsResponse(
        List<ChatRoomResponse> rooms,
        int totalCount
) {
    public static ChatRoomsResponse of(List<ChatRoomResponse> rooms) {
        return new ChatRoomsResponse(rooms, rooms.size());
    }
}