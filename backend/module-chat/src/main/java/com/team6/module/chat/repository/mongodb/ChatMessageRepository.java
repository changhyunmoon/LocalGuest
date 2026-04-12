package com.team6.module.chat.repository.mongodb;

import com.team6.module.chat.entity.mongodb.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    /**
     * 특정 방에서 내 마지막 읽은 시점 이후에 생성된 타인의 메시지 개수 조회 (unreadCount 계산용)
     */
    long countByRoomIdAndCreatedAtAfter(String roomId, LocalDateTime lastReadAt);

    /**
     * 특정 방의 메시지 중, lastMessageId보다 이전에 생성된 메시지들을 최신순으로 조회
     */
    @Query(value = "{ 'roomId': ?0, '_id': { $lt: ?1 } }", sort = "{ '_id': -1 }")
    List<ChatMessage> findBeforeId(String roomId, org.bson.types.ObjectId lastMessageId, Pageable pageable);

    /**
     * lastMessageId가 없는 경우(첫 페이지) 최신순 조회
     */
    @Query(value = "{ 'roomId': ?0 }", sort = "{ '_id': -1 }")
    List<ChatMessage> findByRoomIdOrderByIdDesc(String roomId, Pageable pageable);

}