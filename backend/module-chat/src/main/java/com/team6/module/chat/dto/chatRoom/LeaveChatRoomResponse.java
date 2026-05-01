package com.team6.module.chat.dto.chatRoom;

/**
 * 채팅방 퇴장 API 응답. HTTP 4xx 시에도 동일 필드로 실패 사유를 전달한다.
 */
public record LeaveChatRoomResponse(
        boolean success,
        String message,
        /** 퇴장 처리 후 서버에서 방·메시지가 완전히 삭제됐는지 */
        boolean roomDeleted
) {
    public static LeaveChatRoomResponse ok(boolean roomDeleted) {
        return new LeaveChatRoomResponse(true, "채팅방에서 퇴장했습니다.", roomDeleted);
    }

    public static LeaveChatRoomResponse fail(String message) {
        return new LeaveChatRoomResponse(false, message, false);
    }
}
