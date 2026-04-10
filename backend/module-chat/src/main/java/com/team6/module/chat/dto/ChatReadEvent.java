package com.team6.module.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReadEvent {
    private String roomId;
    private Long userId;
    private String type; // "READ" 등으로 구분
}