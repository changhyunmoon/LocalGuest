package com.team6.module.chat.dto.notification;

import lombok.Builder;

@Builder
public record ChatNotificationResponse(
        String type,          // CONNECT, NEW_ROOM, NEW_MESSAGE, ROOM_LEFT
        String roomId,        // 이벤트 발생 방 ID
        String senderEmail,   // 메시지 발신자 (본인 제외용)
        String receiverEmail, // 특정 수신자 (초대 시 사용)
        Object data           // 추가 데이터 (ChatRoomResponse 등)
) {
    // 공통 정적 팩토리 메서드
    public static ChatNotificationResponse of(String type, String roomId, String senderEmail, String receiverEmail, Object data) {
        return new ChatNotificationResponse(type, roomId, senderEmail, receiverEmail, data);
    }
}