package com.team6.module.chat.dto.chatMessage;

import org.springframework.data.domain.Slice;
import java.util.List;

public record ChatPagingResponse<T>(
        List<T> content,        // 데이터 리스트
        int currentPage,        // 현재 페이지 번호
        int size,               // 페이지 크기
        boolean hasNext         // 다음 페이지 존재 여부 (무한 스크롤 핵심)
) {
    public static <T> ChatPagingResponse<T> from(Slice<T> slice) {
        return new ChatPagingResponse<>(
                slice.getContent(),
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext()
        );
    }
}