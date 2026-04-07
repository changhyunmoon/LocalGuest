package com.team6.module.chat.repository.mongodb;

import com.team6.module.chat.entity.mongodb.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {


    // 특정 방에서 특정 시간(lastReadAt) 이후에 생성된 메시지 개수 조회
    long countByRoomIdAndCreatedAtAfter(String roomId, LocalDateTime lastReadAt);
}