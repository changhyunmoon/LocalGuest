package com.team6.module.chat.dto.response;

//상대방의 입장/퇴장으로 인한 UI 갱신 신호를 보낼 때 사용합니다.
public record ChatStatusResponse(
        String type,      // "READ_UPDATE"
        String roomId,
        String userEmail
) {
    public static ChatStatusResponse of(String roomId, String userEmail) {
        return new ChatStatusResponse("READ_UPDATE", roomId, userEmail);
    }
}
