package com.team6.module.chat.dto.response;

import com.team6.module.chat.entity.mysql.ChatRoom;

import java.time.LocalDateTime;

public record ChatRoomListResponse(
        String roomId,
        String title,
        int participantCount,
        Long unreadCount,      // 안 읽은 메시지 개수 (0이면 안 읽은 메시지 없음)
        String lastMessage,    // 마지막 채팅 내용
        LocalDateTime lastMessageAt // 마지막 채팅 시간 (정렬 기준)
) {}