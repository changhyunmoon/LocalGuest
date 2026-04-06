package com.team6.module.chat.dto.response;

import java.util.List;

public record ChatRoomGroupedListResponse(
        List<ChatRoomListResponse> unreadRooms,
        List<ChatRoomListResponse> readRooms
) {
}

