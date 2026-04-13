package com.team6.module.chat.repository.mongodb;

import com.team6.module.chat.entity.mongodb.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    //특정 방에서 사용자의 마지막 읽은 시점 이후에 생성된 메시지 개수 조회
    long countByRoomIdAndCreatedAtAfter(String roomId, LocalDateTime lastReadAt);

    // 특정 채팅방의 메시지를 페이지 단위로 조회 (Slice 사용으로 count 쿼리 생략)
    Slice<ChatMessage> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);
}
