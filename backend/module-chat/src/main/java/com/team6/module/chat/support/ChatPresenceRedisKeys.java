package com.team6.module.chat.support;

/**
 * STOMP presence({@code PresenceInterceptor})와 메시지 unread 계산({@code ChatMessageService})이
 * 동일한 Redis 키를 쓰도록 prefix를 한곳에서 관리한다.
 */
public final class ChatPresenceRedisKeys {

    private ChatPresenceRedisKeys() {
    }

    public static final String ROOM_PARTICIPANTS_PREFIX = "CHAT_ROOM_PARTICIPANTS:";
    public static final String USER_SESSION_PREFIX = "USER_SESSION:";

    public static String roomParticipantsKey(String roomId) {
        return ROOM_PARTICIPANTS_PREFIX + roomId;
    }

    public static String userSessionKey(String sessionId) {
        return USER_SESSION_PREFIX + sessionId;
    }
}
