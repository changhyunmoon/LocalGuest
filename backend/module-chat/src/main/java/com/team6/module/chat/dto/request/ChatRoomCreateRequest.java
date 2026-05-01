package com.team6.module.chat.dto.request;

import java.util.List;

public record ChatRoomCreateRequest(
        String title,
        List<String> participantEmails
) {
}